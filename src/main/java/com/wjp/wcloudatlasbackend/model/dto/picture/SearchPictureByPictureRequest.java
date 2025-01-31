package com.wjp.wcloudatlasbackend.model.dto.picture;

import lombok.Data;

/**
 * 以图搜图请求
 * @author wjp
 */
@Data
public class SearchPictureByPictureRequest {

    /**
     * 图片id
     */
    private Long pictureId;

    private static final long serialVersionUID = -4289982058773032480L;

}
