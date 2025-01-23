package com.wjp.wcloudatlasbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片上传请求对象
 * @author wjp
 */
@Data
public class PictureUploadRequest implements Serializable {
    /**
     * 图片id (用于上传)
     */
    private Long id;

    /**
     * 图片路径(在线地址)
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private String tags;



    private static final long serialVersionUID = -4289982058773032480L;
}
