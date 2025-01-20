package com.wjp.wcloudatlasbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片更新请求
 * @author wjp
 */
@Data
public class PictureEditRequest implements Serializable {
    /**
     * 图片id (用于上传)
     */
    private Long id;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签（JSON 数组）
     */
    private List<String> tags;


    private static final long serialVersionUID = -4289982058773032480L;
}
