package com.wjp.wcloudatlasbackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

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

    private static final long serialVersionUID = -4289982058773032480L;
}
