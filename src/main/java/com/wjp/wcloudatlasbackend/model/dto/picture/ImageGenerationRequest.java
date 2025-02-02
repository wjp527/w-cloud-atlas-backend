package com.wjp.wcloudatlasbackend.model.dto.picture;

import com.wjp.wcloudatlasbackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import lombok.Data;

/**
 * 图配文模型 服务请求参数
 * @author wjp
 */
@Data
public class ImageGenerationRequest {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 扩图参数
     */
    private CreateOutPaintingTaskRequest.Parameters parameters;

    private static final long serialVersionUID = 1L;
}
