package com.wjp.wcloudatlasbackend.model.dto.space.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 公共的图片分析类
 * @author wjp
 */
@Data
public class SpaceAnalyzeRequest implements Serializable {
    /**
     * 空间id
     */
    private Long spaceId;

    /**
     * 是否查询公共图库
     */
    private boolean queryPublic;

    /**
     * 全空间分析
     */
    private boolean queryAll;

    private static final long serialVersionUID = 1L;

}
