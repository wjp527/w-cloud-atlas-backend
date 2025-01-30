package com.wjp.wcloudatlasbackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wjp.wcloudatlasbackend.annotation.AuthCheck;
import com.wjp.wcloudatlasbackend.common.BaseResponse;
import com.wjp.wcloudatlasbackend.common.DeleteRequest;
import com.wjp.wcloudatlasbackend.common.ResultUtils;
import com.wjp.wcloudatlasbackend.constant.UserConstant;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.model.dto.user.*;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.vo.user.LoginUserVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import com.wjp.wcloudatlasbackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户管理
 * @author wjp
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    // 加盐
    private static final String SLAT = "wjp";
    /**
     * 用户注册
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if(userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        long result = userService.userRegister(userRegisterRequest);

        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     * @param userLoginRequest 登录参数
     * @param request http请求
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 1. 获取登录参数
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        // 2. 参数校验
        if(StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户信息
     * @param request http请求
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if(loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        // 返回脱敏后的数据(即: VO)
        LoginUserVO loginUserVO = userService.getLoginUserVO(loginUser);
        return ResultUtils.success(loginUserVO);

    }

    /**
     * 用户登录
     * @param request http请求
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null ,ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);

        return ResultUtils.success(result);
    }

    /**
     * 创建用户(仅管理员)
     * @return
     */
    @PostMapping("/add")
    // 添加管理员权限
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> userAdd(@RequestBody UserAddRequest userAddRequest) {
        if(userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        // 构建用户对象
        User user = new User();
        // 设置默认值
        String password = "12345678";
        String encryptPassword = userService.getEncryptPassword(password);
        user.setUserPassword(encryptPassword);
        // 复制属性
        BeanUtil.copyProperties(userAddRequest, user);

        // 保存用户
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.PARAMS_ERROR, "操作异常");

        // 用户的id
        Long id = user.getId();
        return ResultUtils.success(id);
    }


    /**
     * 根据id获取用户信息 (仅管理员)
     * @param id
     * @return
     */
    @GetMapping("/get")
    // 添加管理员权限
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        if(id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        // 根据id获取用户
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null ,ErrorCode.PARAMS_ERROR);
        
        return ResultUtils.success(user);
    }

    /**
     * 根据id获取用户信息 (用户)
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 删除用户 (仅管理员)
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        String id = deleteRequest.getId();
        if(StrUtil.isBlank(id)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        // 根据id获取用户
        boolean result = userService.removeById(id);
        // 删除失败
        ThrowUtils.throwIf(!result, ErrorCode.PARAMS_ERROR, "操作异常");
        
        return ResultUtils.success(true);

    }

    /**
     * 更新用户 (仅管理员)
     * @param userUpdateRequest 更新用户请求
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        // 校验参数
        if(userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        // 构建User对象
        User user = new User();
        // 复制属性
        BeanUtil.copyProperties(userUpdateRequest, user);
        // 更新用户
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.PARAMS_ERROR,"操作异常");
        return ResultUtils.success(true);
    }


    /**
     * 分页查询用户 (仅管理员)
     * @param userQueryRequest 分页查询参数
     * @return
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR, "参数为空");

        // 获取分页参数
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        // 获取用户的分页列表
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest));

        // 获取VO的分页列表
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        // 将用户列表转化为VO列表
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        // 更新VO分页列表
        userVOPage.setRecords(userVOList);

        return ResultUtils.success(userVOPage);

    }

}
