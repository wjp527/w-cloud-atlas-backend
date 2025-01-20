package com.wjp.wcloudatlasbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.manager.FileManager;
import com.wjp.wcloudatlasbackend.model.dto.file.UploadPictureResult;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureUploadRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import com.wjp.wcloudatlasbackend.service.PictureService;
import com.wjp.wcloudatlasbackend.mapper.PictureMapper;
import com.wjp.wcloudatlasbackend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    /**
     * 上传图片
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return 返回 PictureVO
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null , ErrorCode.NO_AUTH_ERROR);
        // 用于判断是否新增还是更新图片
        Long pictureId = null;
        if(pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        if(pictureId != null) {
            // 判断图片是否存在
            boolean exists = this.lambdaQuery()
                    // 这里是 根据 Picture中的id 与 pictureId 查询图片是否存在
                    .eq(Picture::getId, pictureId)
                    // 会根据前面的查询条件，如果前面条件为true，则返回true，否则返回false
                    .exists();

            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }


        // 上传图片，得到信息
        // 按照用户id，划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setName(uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());

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
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
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
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjectUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjectUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjectUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjectUtil.isNotEmpty(picScale), "picScale", picScale);

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

}




