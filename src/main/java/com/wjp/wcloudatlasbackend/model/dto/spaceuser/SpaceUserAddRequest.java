package com.wjp.wcloudatlasbackend.model.dto.spaceuser;

import lombok.Data;

import java.io.Serializable;

/**
 * 新增空间用户请求参数
 * @author wjp
 */

@Data
public class SpaceUserAddRequest implements Serializable {
    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间角色：viewer/editor/admin
     */
    private String spaceRole;

    private static final long serialVersionUID = 1L;
}
