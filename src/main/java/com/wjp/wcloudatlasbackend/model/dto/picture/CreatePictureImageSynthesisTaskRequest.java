package com.wjp.wcloudatlasbackend.model.dto.picture;

import com.wjp.wcloudatlasbackend.api.aliyunai.model.CreateImageSynthesisTaskRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 扩图服务请求参数
 * @author wjp
 */
@Data
public class CreatePictureImageSynthesisTaskRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 给图片添加的标题
     */
    private List<String> title;

    /**
     * 扩图参数
     */
    private CreateImageSynthesisTaskRequest.Parameters parameters;

    private static final long serialVersionUID = 1L;
}
