package com.wjp.wcloudatlasbackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求
 * @author wjp
 */
@Data
public class UserLoginRequest implements Serializable {

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户密码
     */
    private String userPassword;


    private static final long serialVersionUID = -8887330512914130151L;
}
