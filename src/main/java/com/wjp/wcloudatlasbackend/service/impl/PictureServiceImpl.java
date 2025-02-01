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
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.manager.CosManager;
import com.wjp.wcloudatlasbackend.manager.FileManager;
import com.wjp.wcloudatlasbackend.manager.upload.FilePictureUpload;
import com.wjp.wcloudatlasbackend.manager.upload.PictureUploadTemplate;
import com.wjp.wcloudatlasbackend.manager.upload.UrlPictureUpload;
import com.wjp.wcloudatlasbackend.model.dto.file.UploadPictureResult;
import com.wjp.wcloudatlasbackend.model.dto.picture.*;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.PictureBatchEnum;
import com.wjp.wcloudatlasbackend.model.enums.PictureReviewStatusEnum;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import com.wjp.wcloudatlasbackend.service.PictureService;
import com.wjp.wcloudatlasbackend.mapper.PictureMapper;
import com.wjp.wcloudatlasbackend.service.SpaceService;
import com.wjp.wcloudatlasbackend.service.UserService;
import com.wjp.wcloudatlasbackend.utils.ColorSimilarUtils;
import com.wjp.wcloudatlasbackend.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.descriptor.web.JspPropertyGroup;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
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

    @Resource
    private CosManager cosManage;

    @Resource
    private SpaceService spaceService;

    /**
     * 事务模板
     */
    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 上传图片
     * @param inputSource 图片来源【文件传输 / URL路径】
     * @param pictureUploadRequest  图片上传请求参数
     * @param loginUser 登录用户
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

        Long spaceId = null;
        // 校验空间是否存在
        if (pictureUploadRequest != null && pictureUploadRequest.getSpaceId() != null) {
            spaceId = pictureUploadRequest.getSpaceId();
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验是否有空间的权限，仅空间管理员才能上传
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权上传图片");
            }

            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间额度不足");
            }

            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间容量不足");
            }
        }


        // 如果是更新图片，需要校验图片是否存在
        if(pictureId != null) {

            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            // 仅本人和管理员有权编辑图片
            if(!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权更新");
            }

            // 校验空间是否一致
            // 没传 spaceId, 则复用原有图片的 spaceId（这样也兼容了公共图库）
            if(spaceId == null) {
                if(oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                } else {
                    // 传了 spaceId,必须和原图的空间id 保持一致
                    if(ObjectUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间id不一致");
                    }
                }
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
        String uploadPathPrefix;
        if(spaceId == null) {
            // 公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            // 空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        // 根据 inputSource 类型 区分上传方式
        // 这里用到了 多态
        // 这里是用 图片上传
        PictureUploadTemplate pictureUploadTemplate =  FilePictureUpload;
        // 如果url是字符串类型，那他就是 通过 url 进行导入图片的
        if(inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }

        // 上传图片
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        // 设置缩略图
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();
        if(picName != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setSpaceId(spaceId); // 指定空间id
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        // 设置颜色主色调
        // picture.setPicColor(uploadPictureResult.getPicColor());
        picture.setPicColor(ColorTransformUtils.getStanderColor(uploadPictureResult.getPicColor()));

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


        // 开启事务
        Long finalSpaceId = spaceId;

            transactionTemplate.execute(status -> {
                boolean result = this.saveOrUpdate(picture);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
                if(finalSpaceId!=null) {
                    // 更新空间的使用额度
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, finalSpaceId)
                            .setSql("totalSize = totalSize + " + picture.getPicSize())
                            .setSql("totalCount = totalCount + 1")
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "空间额度更新失败");
                }
                return picture;
            });




        // todo 如果是更新，可清理cos中的图片资源
        if(pictureId != null) {
            this.clearPictureFile(picture);
        }

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
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();


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
        queryWrapper.eq(ObjectUtil.isNotNull(spaceId),"spaceId",spaceId);
        // 空间id为空时，查询所有空间图片
        queryWrapper.isNull(nullSpaceId, "spaceId");
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
        // ge: 大于等于，gt: 大于，le: 小于等于，lt: 小于
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

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

    /**
     * 清理 cos 中的图片文件
     * @param oldPicture
     */
    @Async // 异步调用, ✨ 记得在 启动文件中 开启@EnableAsync注解
    @Override
    public void clearPictureFile(Picture oldPicture) {
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        // 计算 图片被使用次数
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();

        if(count > 1) {
            return ; // 图片被多条记录使用，不删除
        }

      try {
          // 删除图片
          String picturePath = new URL(pictureUrl).getPath();
          cosManage.deleteObject(picturePath);

          // 缩略图
          String thumbnailUrl = oldPicture.getThumbnailUrl();
          if(StrUtil.isNotBlank(thumbnailUrl)) {
              // 删除缩略图
              String thumbnailPath = new URL(thumbnailUrl).getPath();
              cosManage.deleteObject(thumbnailPath);
          }
      } catch(MalformedURLException e) {
          log.error("图片地址错误：{}", pictureUrl);
          throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址错误");
      }

    }

    /**
     * 删除图片
     * @param pictureId
     * @param pictureIds
     * @param loginUser
     */
    @Override
    public void deletePicture(String pictureId, List<String> pictureIds,User loginUser) {

        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");



        boolean result ;

        if(pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");



            // 开启事务
            transactionTemplate.execute(status -> {
                // 操作数据库
                boolean deleteResult = this.removeById(pictureId);
                ThrowUtils.throwIf(!deleteResult, ErrorCode.OPERATION_ERROR, "删除失败");
                // 更新空间的使用额度
                if(oldPicture.getSpaceId() != null) {
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, oldPicture.getSpaceId())
                            .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                            .setSql("totalCount = totalCount - 1")
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "空间额度更新失败");
                }
                return true;
            });

            // 校验权限
            this.checkPictureAuth(loginUser, oldPicture);
            // 删除 cos 中的图片
            this.clearPictureFile(oldPicture);


        } else if(pictureIds != null && pictureIds.size() > 0) {
            // 批量删除
            this.removeByIds(pictureIds);
        }
    }

    /**
     * 编辑图片信息
     * @param pictureEditRequest 编辑请求
     * @param loginUser 登录用户
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类 和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureEditRequest, picture);

        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);

        // 补充审核参数
        this.fillReviewParams(picture, loginUser);

        // 判断是否存在
        Long id = picture.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 校验权限
        this.checkPictureAuth(loginUser, oldPicture);

        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.PARAMS_ERROR, "编辑失败");
    }

    /**
     * 校验空间图片的权限
     * @param loginUser 登录用户
     * @param picture 图片
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();

        // 空间为null，表示公共图库
        if(spaceId == null) {
            // 公共图库，仅本人和管理员可操作
            if(!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "非本人或管理员无权操作");
            }
        } else {
            // 私有空间 仅空间管理员可操作
            if(!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "非本人无权操作");
            }
        }

    }

    /**
     * 根据颜色值搜索图片
     * @param spaceId 空间id
     * @param pictColor 颜色值
     * @param loginUser 登录用户
     * @return 图片列表
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String pictColor, User loginUser) {

        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(pictColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if(!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "非本人无权操作");
        }

        // 3. 查询该控件下所有图片(必须有主色调)
        List<Picture> pictureList = this.lambdaQuery()
                // 找到 该控件下所有图片
                .eq(Picture::getSpaceId, spaceId)
                // 找到有主色调的图片
                .isNotNull(Picture::getPicColor)
                // 整合为列表
                .list();

        // 3.1. 如果没有图片，直接返回空列表
        if(CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }

        // 3.2. 将目标颜色转为 Color 对象
        Color targetColor = Color.decode(pictColor);

        // 4. 计算颜色相似度并排序
        List<Picture> sortedPictures = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    // 提取图片主色调
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片放到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 越大越相似，但是这里在 sort中，越大的值回往后面排，所以这里需要 * -1
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12)
                .collect(Collectors.toList());

        // 5. 转换为 VO 对象
        return sortedPictures.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

    }

    /**
     * 批量编辑图片
     * @param pictureEditByBatchRequest 编辑请求
     * @param loginUser 登录用户
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        ThrowUtils.throwIf(pictureIdList == null || pictureIdList.size() == 0, ErrorCode.PARAMS_ERROR, "图片id列表为空");
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR, "空间id为空");


        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if(!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }


        // 3. 查询指定图片(仅选择需要的字段)
        List<Picture> pictureList = this.lambdaQuery()
                // 查询 指定空间的 指定图片
                .select(Picture::getId, Picture::getSpaceId)
                // 找到 对应空间下的图片
                .eq(Picture::getSpaceId, spaceId)
                // 找到对应的图片id
                .in(Picture::getId, pictureIdList)
                // 整合为列表
                .list();
        if(CollUtil.isEmpty(pictureList)) {
            return ;
        }

        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if(StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if(CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });

        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);

        // 5. 操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }

    /**
     * 批量重命名
     * nameRule 格式: 图片{序号}
     * @param pictureList 图片id列表
     * @param nameRule  命名规则
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if(CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return ;
        }
        long count = 1;
        try {
            for(Picture picture : pictureList) {
                if (picture == null) {
                    continue;
                }
                String name = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(name);
            }
        } catch(Exception e){
            log.error("批量重命名失败：{}", e);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "命名规则错误");
        }
    }


}




