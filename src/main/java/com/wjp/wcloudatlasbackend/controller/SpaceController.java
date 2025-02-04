package com.wjp.wcloudatlasbackend.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wjp.wcloudatlasbackend.annotation.AuthCheck;
import com.wjp.wcloudatlasbackend.common.BaseResponse;
import com.wjp.wcloudatlasbackend.common.DeleteRequest;
import com.wjp.wcloudatlasbackend.common.ResultUtils;
import com.wjp.wcloudatlasbackend.constant.UserConstant;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceAddRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceEditRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceUpdateRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.vo.space.SpaceVO;
import com.wjp.wcloudatlasbackend.service.SpaceService;
import com.wjp.wcloudatlasbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * 空间接口
 * @author wjp
 */
@RestController
@RequestMapping("/space")
@Slf4j
public class SpaceController {

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;


    /**
     * 新增空间
     * @param spaceAddRequest 新增请求
     * @param request 请求
     * @return 新增的空间id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR, "参数为空");
        User loginUser = userService.getLoginUser(request);
        long newId = spaceService.addSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(newId);
    }

    /**
     * 删除空间
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if(deleteRequest == null || deleteRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }

        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        if(userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }

        String id = deleteRequest.getId();

        // 获取要删除的数据
        Space oldSpace = spaceService.getById(id);
        if (oldSpace == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数据不存在");
        }

        // 仅本人和管理员可删除
        spaceService.checkSpaceAuth(loginUser, oldSpace);

        // 操作数据库
        boolean result = spaceService.removeById(id);
        ThrowUtils.throwIf(!result,ErrorCode.PARAMS_ERROR,"删除失败");


        return ResultUtils.success(true);
    }


    /**
     * 更新空间(管理员权限)
     * @param spaceUpdateRequest 更新请求
     * @param request 请求
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        if(spaceUpdateRequest == null || spaceUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        // 将实体类 和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验
        spaceService.validSpace(space, false);
        // 判断是否存在
        Long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.PARAMS_ERROR, "数据不存在");

        // 操作数据库
        boolean result = spaceService.updateById(space);

        ThrowUtils.throwIf(!result,ErrorCode.PARAMS_ERROR,"更新失败");
        return ResultUtils.success(true);


    }

    /**
     * 根据 id 获取空间(管理员权限)
     * @param id 空间id
     * @param request 请求
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "参数错误");
        
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "数据不存在");

        //返回封装类
        return ResultUtils.success(space);
    }

    /**
     * 根据 id 获取空间 VO(普通用户)
     * @param id 空间id
     * @param request 请求
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "参数错误");

        // 查询数据库
        Space space = spaceService.getById(id);
        // 转为 VO
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);

        return ResultUtils.success(spaceVO);

    }

    /**
     * 分页查询空间(管理员权限)
     * @param spaceQueryRequest 查询请求
     * @return 分页结果
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR, "参数为空");

        int current = spaceQueryRequest.getCurrent();
        int pageSize = spaceQueryRequest.getPageSize();
        
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, pageSize), spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);

    }

    /**
     * 分页查询空间 VO(普通用户)
     * @param spaceQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVoByPage(@RequestBody SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR, "参数为空");
        
        int current = spaceQueryRequest.getCurrent();
        int pageSize = spaceQueryRequest.getPageSize();

        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);

        // 获取分页数据
        Page<Space> spacePage = spaceService.page(new Page<>(current, pageSize), spaceService.getQueryWrapper(spaceQueryRequest));
        // 转为 VO
        Page<SpaceVO> spaceVOPage = spaceService.getSpaceVOPage(spacePage, request);

        return ResultUtils.success(spaceVOPage);
    }


    /**
     * 修改空间(普通用户)
     * @param spaceEditRequest 编辑请求
     * @param request 请求
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        if(spaceEditRequest == null || spaceEditRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        // 在此处将 实体类 转为 DTO
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 设置编辑时间
        space.setEditTime(new Date());
        // 数据校验
        spaceService.validSpace(space, false);

        User loginUser = userService.getLoginUser(request);
        // 判断数据库是否存在
        Long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        if(oldSpace == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "数据不存在");
        }

        // 只有 自己 和 管理员 才能修改
        spaceService.checkSpaceAuth(loginUser, space);

        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"更新失败");

        return ResultUtils.success(true);


    }






























}
