package com.wjp.wcloudatlasbackend.api.aliyunai.model;

import cn.hutool.core.annotation.Alias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建扩图任务请求
 * @author wjp
 */
@Data
public class CreateImageSynthesisTaskRequest implements Serializable {

    /**
     * 模型，例如 "wanx-ast"
     * AI图配文模型
     */
    private String model = "wanx-ast";

    /**
     * 输入图像信息
     */
    private Input input;

    /**
     * 图像处理参数
     */
    private Parameters parameters;

    @Data
    public static class Input {
        /**
         * 必选，图像 URL
         */
        @Alias("image_url")
        private String imageUrl;

        /**
         * 必选，标题文本列表
         */
        private List<String> title;      // 待添加的标题文本
    }

    @Data
    public static class Parameters implements Serializable {
        /**
         * 必选，期望生成的图片数量
         */
        private Integer n;
    }
}
