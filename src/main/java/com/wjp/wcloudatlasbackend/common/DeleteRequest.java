package com.wjp.wcloudatlasbackend.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 删除请求
 * @author wjp
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * 删除单个 id
     */
    private String id;

    /**
     * 批量删除id
     */
    private List<String> ids;

    private static final long serialVersionUID = 1L;
}
