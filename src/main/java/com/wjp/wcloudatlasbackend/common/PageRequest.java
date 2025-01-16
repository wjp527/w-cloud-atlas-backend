package com.wjp.wcloudatlasbackend.common;

import lombok.Data;

/**
 * 通用分页请求参数封装
 * @author wjp
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 当前页码
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序(默认降序)
     */
    private String sortOrder = "descend";

}
