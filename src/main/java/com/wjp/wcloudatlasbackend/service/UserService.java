package com.wjp.wcloudatlasbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wjp.wcloudatlasbackend.model.dto.user.UserQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.user.UserRegisterRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wjp.wcloudatlasbackend.model.vo.user.LoginUserVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author wjp
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-01-17 19:26:44
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param userRegisterRequest 用户注册请求参数
     * @return
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 用户登录
     * @param userAccount 用户账号
     * @param userPassword 用户密码
     * @param request 请求对象 - 用于种session信息
     * @return
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);


    /**
     * 获取当前登录用户信息
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取加密后的密钥
     * @param userPassword 用户密码
     * @return 返回加密后的密钥
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取脱敏类的用户登录信息
     * @param user 用户
     * @return 返回脱敏后的用户信息
     */
    LoginUserVO getLoginUserVO(User user);


    /**
     * 获取脱敏类的用户信息
     * @param user 用户
     * @return 返回脱敏后的用户信息
     */
    UserVO getUserVO(User user);


    /**
     * 获取脱敏类的用户信息列表
     * @param userList 用户
     * @return 返回脱敏后的用户信息
     */
    List<UserVO> getUserVOList(List<User> userList);


    /**
     * 获取查询条件【整合查询的SQL语句】
     * @param userQueryRequest 查询条件
     * @return 返回查询条件的QueryWrapper
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


    /**
     * 是否为管理员
     * @param user
     * @return
     */
    boolean isAdmin(User user);

}
