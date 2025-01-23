package com.wjp.wcloudatlasbackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.wjp.wcloudatlasbackend.annotation.AuthCheck;
import com.wjp.wcloudatlasbackend.common.BaseResponse;
import com.wjp.wcloudatlasbackend.common.DeleteRequest;
import com.wjp.wcloudatlasbackend.common.ResultUtils;
import com.wjp.wcloudatlasbackend.constant.UserConstant;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.manager.CosManager;
import com.wjp.wcloudatlasbackend.model.dto.picture.*;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.PictureReviewStatusEnum;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureTagCategory;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureVO;
import com.wjp.wcloudatlasbackend.service.PictureService;
import com.wjp.wcloudatlasbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 图片接口
 *
 * @author <a href="https://github.com/liwjp">程序员鱼皮</a>
 * @from <a href="https://wjp.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;


    /**
     * 上传图片(可重新上传)
     * @param multipartFile 图片文件
     * @param pictureUploadRequest 图片上传请求
     * @param response 响应
     * @param request 请求
     * @return 图片信息
     */
    @PostMapping("/upload")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile,
                                                 PictureUploadRequest pictureUploadRequest,
                                                 HttpServletResponse response,
                                                 HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);

    }

    /**
     * 删除图片
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 校验参数
        if(deleteRequest == null || deleteRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        // 图片id
        String id = deleteRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 仅本人和管理员可删除
        Long userId = loginUser.getId();
        if(!oldPicture.getUserId().equals(userId) || !userService.isAdmin(loginUser)) {
            throw  new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除");
        }

        // 操作数据库
        boolean result = pictureService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");

        return ResultUtils.success(true);

    }

    /**
     * 更新图片信息（仅管理员可用）
     * @param pictureUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        // 检验参数
        if(pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        // 将实体类 和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(picture.getTags()));
        // 数据校验
        pictureService.validPicture(picture);

        // 判断是否存在
        Long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);

        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新失败");

        return ResultUtils.success(result);
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "参数为空");

        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片 (封装类)
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "参数为空");

        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 分页获取图片列表(仅管理员可见)
     * @param pictureQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        // 查询数据库
        // 根据 查询条件进行 分页
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        
        return ResultUtils.success(picturePage);

    }


    /**
     * 分页获取图片列表(封装类) 【多条数据】
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        // 限制爬虫
       ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);

       // ✨普通用户 只查找 审核通过的 数据
       pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

       // 查询数据库
        // 根据 查询条件进行 分页
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        // 封装成 VO
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        return ResultUtils.success(pictureVOPage);
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {

        if(pictureEditRequest == null || pictureEditRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 在此处将实体类 和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureEditRequest, picture);

        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        pictureService.validPicture(picture);

        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);

        // 判断是否存在
        Long id = picture.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 仅本人或管理员可以编辑
        if(!oldPicture.getUserId().equals(userId) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权编辑");
        }

        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.PARAMS_ERROR, "编辑失败");

        return ResultUtils.success(true);

    }


    /**
     * 获取图片标签分类
     * @param request
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> pictureTagCategory(HttpServletRequest request) {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "艺术", "校园", "创意", "运动", "科技");
        List<String> categoryList = Arrays.asList("模版", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        
        return ResultUtils.success(pictureTagCategory);
    }


    /**
     * 图片审核(管理员可用)
     * @param pictureReviewRequest
     * @param request
     * @return
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {

        ThrowUtils.throwIf(pictureReviewRequest == null , ErrorCode.PARAMS_ERROR, "参数为空");

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");


        pictureService.doPictureReview(pictureReviewRequest, loginUser);

        return ResultUtils.success(true);



    }
}
