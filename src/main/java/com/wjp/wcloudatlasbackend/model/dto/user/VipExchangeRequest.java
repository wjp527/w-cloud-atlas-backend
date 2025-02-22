package com.wjp.wcloudatlasbackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 会员兑换请求
 * @author wjp
 */
@Data
public class VipExchangeRequest implements Serializable {

    /**
     * 兑换码
     */
    private String vipCode;

    private static final long serialVersionUID = 6966011187158778788L;
}
