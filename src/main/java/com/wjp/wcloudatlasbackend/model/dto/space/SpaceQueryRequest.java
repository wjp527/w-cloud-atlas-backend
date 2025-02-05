package com.wjp.wcloudatlasbackend.model.dto.space;

import com.wjp.wcloudatlasbackend.common.PageRequest;
import lombok.Data;

import java.io.Serializable;

/**
 * 空间查询请求
 */
@Data
public class SpaceQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     */
    private Integer spaceLevel;

    /**
     * 空间类型：0-私有空间 1-团队空间
     */
    private Integer spaceType;

    private static final long serialVersionUID = 1L;
}
