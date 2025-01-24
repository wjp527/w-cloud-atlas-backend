package com.wjp.wcloudatlasbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.manager.FileManager;
import com.wjp.wcloudatlasbackend.manager.upload.FilePictureUpload;
import com.wjp.wcloudatlasbackend.manager.upload.PictureUploadTemplate;
import com.wjp.wcloudatlasbackend.manager.upload.UrlPictureUpload;
import com.wjp.wcloudatlasbackend.model.dto.file.UploadPictureResult;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureReviewRequest;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureUploadByBatchRequest;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureUploadRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.PictureBatchEnum;
import com.wjp.wcloudatlasbackend.model.enums.PictureReviewStatusEnum;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import com.wjp.wcloudatlasbackend.service.PictureService;
import com.wjp.wcloudatlasbackend.mapper.PictureMapper;
import com.wjp.wcloudatlasbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author wjp
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-01-19 16:56:42
*/
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload FilePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    /**
     * 上传图片
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return 返回 PictureVO
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null , ErrorCode.NO_AUTH_ERROR);
        // 用于判断是否新增还是更新图片
        Long pictureId = null;
        if(pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        if(pictureId != null) {

            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            // 仅本人和管理员有权编辑图片
            if(!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权更新");
            }

//            // 判断图片是否存在
//            boolean exists = this.lambdaQuery()
//                    // 这里是 根据 Picture中的id 与 pictureId 查询图片是否存在
//                    .eq(Picture::getId, pictureId)
//                    // 会根据前面的查询条件，如果前面条件为true，则返回true，否则返回false
//                    .exists();
//
//            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }


        // 上传图片，得到信息
        // 按照用户id，划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());

        // 根据 inputSource 类型 区分上传方式
        PictureUploadTemplate pictureUploadTemplate =  FilePictureUpload;
        if(inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }

        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        // 支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();
        if(picName != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());


        if(pictureUploadRequest.getTags()!=null) {
            picture.setTags(pictureUploadRequest.getTags());
        }
        if(pictureUploadRequest.getCategory()!=null) {
            picture.setCategory(pictureUploadRequest.getCategory());
        }

        picture.setUserId(loginUser.getId());

        // 补充审核参数
        this.fillReviewParams(picture, loginUser);

        // 操作数据库
        // 如果 pictureId不为空，表示更新，否则是新增
        if(pictureId!=null) {
            // 如果是更新，需要补充id和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");

        return PictureVO.objToVo(picture);

    }

    /**
     * 获取查询结果【封装类】 【单条数据】
     * @param picture
     * @param request
     * @return
     */

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);

        // 查询相关用户信息
        Long userId = pictureVO.getUserId();
        if(userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }

        return pictureVO;
    }


    /**
     * 获取分页查询结果【封装类】 多条
     * @param picturePage
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        // 获取分页数据
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if(CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 对象列表 => 封装类对列表
        List<PictureVO> pictureVOList = pictureList.stream()
                // 将 pictureList 中的每个 picture 对象，转换为 PictureVO 对象
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 1. 关联查询用户信息
        // 1,2,3,4
        Set<Long> userIdSet = pictureList.stream()
                // 将 pictureList 中的每个 picture 对象，转换为 userId
                .map(Picture::getUserId)
                // 整合为 Set
                .collect(Collectors.toSet());

        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2.填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            // 检查 Map 中是否包含指定的键
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });

        // 将更新好的vo数据，设置到分页对象中
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 获取查询条件【整合查询的SQL语句】
     * @param pictureQueryRequest 查询条件
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {

        if(pictureQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "查询条件为空");
        }

        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();


        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();


        // 构建查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 从多字段中搜索
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%xxx%" or introduction like "%xxx%")
            queryWrapper.and(
                     qw ->
                     qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }

        queryWrapper.eq(ObjectUtil.isNotNull(id),"id",id);
        queryWrapper.eq(ObjectUtil.isNotNull(userId),"userId",userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjectUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjectUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjectUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjectUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjectUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjectUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);

        // JSON 数组查询
        if(CollUtil.isNotEmpty(tags)) {
            // and (tag like "%\"Java\%" and like "%\Python\"%")
            for(String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);

        return queryWrapper;
    }


    /**
     * 图片参数校验
     * @param picture
     */

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);

        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();

        // 修改数据时，id不能为空，有参数校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id不能为空");

        if(StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url过长");
        }
        if(StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "introduction过长");
        }
    }

    /**
     * 审核图片
     * @param pictureReviewRequest 审核请求
     * @param loginUser            登录用户
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1、校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum pictureReviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();

        /**
         * id不为空，状态不为空，并且状态不是待审核的状态
         */
        if(id == null || pictureReviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(pictureReviewStatusEnum)) {
            ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "参数错误");
        }
        // 2、判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.PARAMS_ERROR, "图片不存在");


        // 3、校验审核状态是否重复
        if(oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态重复");
        }


        // 4、数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        // 设置 审核人id
        updatePicture.setReviewerId(loginUser.getId());
        // 设置 审核时间
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "审核失败");

    }

    /**
     * 填充审核参数
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        // 管理员
        if(userService.isAdmin(loginUser)) {
            // 管理员添加图片，自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
        }
        // 普通用户
        else {
            // 待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * 批量抓取和创建图片
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 使用 StringBuilder 来构建最终的 searchText
        StringBuilder searchTextBuilder = new StringBuilder();
        String text = pictureUploadByBatchRequest.getSearchText();
        String category = pictureUploadByBatchRequest.getCategory();
        List<String> tags = pictureUploadByBatchRequest.getTags();
        searchTextBuilder.append(text).append(",").append(category);

        tags.forEach(item -> {
            searchTextBuilder.append(",").append(item);
        });

        String searchText = searchTextBuilder.toString();


        Integer count = pictureUploadByBatchRequest.getCount();
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        // 来源 百度 / bing
        String source = pictureUploadByBatchRequest.getSource();
        // 从第几页开始爬取
        Integer page = pictureUploadByBatchRequest.getPage();



        // 前缀为空，默认为搜索词
        if(StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }

        // 格式化数量
        ThrowUtils.throwIf(count == null || count <= 0 || count > 30, ErrorCode.PARAMS_ERROR, "最多30条");



//        PictureBatchEnum enumByValue = PictureBatchEnum.getEnumByValue(source);
//        // 获取来源
//        String sourceValue = enumByValue.getValue();
        Integer uploadCount = 0;
        // 百度来源
        if(PictureBatchEnum.BAIDU.getValue().equals(source)) {
            uploadCount = PictureBatchForBaiDu(searchText, namePrefix, category, tags, page, count, loginUser);
        } else {
            // bing来源
            uploadCount = PictureBatchForBing(searchText, namePrefix, category, tags, page, count, loginUser);
        }


        return uploadCount;
    }


    /**
     * 百度图片批量抓取
     * @return
     */
    @Override
    public Integer PictureBatchForBaiDu(String searchText, String namePrefix, String category, List<String> tags,Integer page, Integer count, User loginUser) {
        /**
         * tn=resultjson_com：必带的参数
         * word：搜索关键词
         * pn：分页数，传入30的倍数，第一次为30，第二次为60，以此类推
         */
        String urlTemplate = "https://images.baidu.com/search/acjson?tn=resultjson_com&word=%s&pn=%s";
        int pageSize = 30;  // 每页数据数量
        int totalCount = 0; // 已查询的总条数
        int uploadCount = 0; // 已上传的图片数量

        // 从指定的起始页开始循环
        while (totalCount < count) {
            int pn = (page - 1) * pageSize; // 计算分页参数
            String url = String.format(urlTemplate, searchText, pn);
            String s = HttpUtil.get(url);
            JSONObject jsonObject = JSONUtil.parseObj(s);
            JSONArray list = jsonObject.getJSONArray("data");

            if (list == null || list.isEmpty()) {
                System.out.println("第" + page + "页无数据，结束查询。");
                break;
            }

            System.out.println("第" + page + "页查询结果 ====");
            for (int j = 0; j < list.size(); j++) {
                if (totalCount >= count) {
                    break; // 达到总查询条数限制
                }

                JSONObject res = list.get(j, JSONObject.class);
                String thumbURL = res.getStr("thumbURL");
                if (thumbURL != null) { // 避免空值
                    totalCount++;
                    System.out.println(totalCount + "=======" + thumbURL);
                }

                // 上传图片
                PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
                // 设置图片名称
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
                pictureUploadRequest.setTags(JSONUtil.toJsonStr(tags));
                pictureUploadRequest.setCategory(category);
                try {
                    PictureVO pictureVO = this.uploadPicture(thumbURL, pictureUploadRequest, loginUser);
                    log.info("上传图片成功：id = {}", pictureVO.getId());
                    uploadCount++;
                } catch(Exception e) {
                    log.error("上传图片失败：{}", e.getMessage());
                    // 上传失败，跳过
                    continue;
                }
            }

            page++; // 查询下一页
        }

        System.out.println("查询结束，共输出 " + totalCount + " 条数据。");
        return uploadCount;

    }

    /**
     * Bing图片批量抓取
     * @return
     */
    @Override
    public Integer PictureBatchForBing(String searchText, String namePrefix, String category, List<String> tags,Integer page, Integer count, User loginUser) {
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);

        Document document;
        try {
            // 抓取页面
            document = Jsoup.connect(fetchUrl).get();
        }
        catch(IOException e) {
            log.error("获取网页失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取网页失败");
        }

        // 解析页面
        // 从返回的HTML文档中查找第一个包含类名 dgControl 的 <div>标签，这是存放图片元素的容器
        Element div = document.getElementsByClass("dgControl").first();
        // 如果没有找到，说明你的div类名找的不对
        if(ObjectUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片失败");
        }

        // 要抓取图片的img类名标签
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            // 获取图片地址
            String fileUrl = imgElement.attr("src");
            if(StrUtil.isBlank(fileUrl)) {
                log.info("图片地址为空，跳过：{}", fileUrl);
                // 如果为空，则跳过
                continue;
            }
            // 处理up上传地址，防止出现转义问题
            // 将 url后面?的参数全部去掉
            int questionMarkIndex = fileUrl.indexOf("?");
            if(questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            // 设置图片名称
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            pictureUploadRequest.setTags(JSONUtil.toJsonStr(tags));
            pictureUploadRequest.setCategory(category);
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("上传图片成功：id = {}", pictureVO.getId());
                uploadCount++;
            } catch(Exception e) {
                log.error("上传图片失败：{}", e.getMessage());
                // 上传失败，跳过
                continue;
            }
            // 上传成功，判断是否达到数量限制
            if(uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

}




