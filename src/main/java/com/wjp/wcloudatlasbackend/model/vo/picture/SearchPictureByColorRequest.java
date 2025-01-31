package com.wjp.wcloudatlasbackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 按照 颜色主色调 进行搜索图片
 * @author wjp
 */
@Data
public class SearchPictureByColorRequest implements Serializable {

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 空间 id
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
