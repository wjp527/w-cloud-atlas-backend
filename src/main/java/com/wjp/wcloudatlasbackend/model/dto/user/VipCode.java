package com.wjp.wcloudatlasbackend.model.dto.user;

import lombok.Data;

/**
 * 兑换码
 * @author wjp
 */
@Data
public class VipCode {
    /**
     * 兑换码
     */
    private String code;

    /**
     * 是否已使用
     */
    private boolean hasUsed;
}
