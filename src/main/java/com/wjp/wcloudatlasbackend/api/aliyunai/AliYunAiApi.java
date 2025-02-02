package com.wjp.wcloudatlasbackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.wjp.wcloudatlasbackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.wjp.wcloudatlasbackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.wjp.wcloudatlasbackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliYunAiApi {

    // 读取 application.yml 配置文件中的 aliYunAi.apiKey
    @Value("${aliYunAi.apiKey}")
    private String apiKey;


    // 创建任务 请求
    // https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting
    public static final String CREATE_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting";

    // 根据任务ID查询结果 请求
    // https://dashscope.aliyuncs.com/api/v1/tasks/{task_id}
    public static final String GET_OUT_PAINTING_TASK_URL = "https://dashscope.aliyuncs.com/api/v1/tasks/%s";

    /**
     * 创建任务
     * @param createOutPaintingTaskRequest 创建任务请求
     * @return  创建任务响应
     */
    public CreateOutPaintingTaskResponse createOutPaintingTask(CreateOutPaintingTaskRequest createOutPaintingTaskRequest) {
        ThrowUtils.throwIf(createOutPaintingTaskRequest == null, ErrorCode.OPERATION_ERROR, "AI 扩图失败");
        // 发起请求
        // curl --location --request POST 'https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/out-painting' \
        //--header "Authorization: Bearer $DASHSCOPE_API_KEY" \
        //--header 'X-DashScope-Async: enable' \
        //--header 'Content-Type: application/json' \
        //--data '{
        //    "model": "image-out-painting",
        //    "input": {
        //        "image_url": "http://xxx/image.jpg"
        //    },
        //    "parameters":{
        //        "angle": 45,
        //        "x_scale":1.5,
        //        "y_scale":1.5
        //    }
        //}
        HttpRequest httpRequest = HttpRequest.post(CREATE_OUT_PAINTING_TASK_URL)
                .header("Authorization", "Bearer " + apiKey)
                // 必须开启异步处理
               .header("X-DashScope-Async", "enable")
                .header("Content-Type", "application/json")
               .body(JSONUtil.toJsonStr(createOutPaintingTaskRequest));

        // 处理异常
        // 自动管理需要关闭的资源(如文件流、网络连接等)
        try(HttpResponse httpResponse = httpRequest.execute()) {
            if(!httpResponse.isOk()) {
                log.error("请求异常: {}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败");
            }

            // 解析响应
            CreateOutPaintingTaskResponse createOutPaintingTaskResponse = JSONUtil.toBean(httpResponse.body(), CreateOutPaintingTaskResponse.class);
            // 解析错误信息
            String errorCode = createOutPaintingTaskResponse.getCode();
            if(StrUtil.isNotBlank(errorCode)) {
                String errorMessage = createOutPaintingTaskResponse.getMessage();
                log.error("请求异常: {}", errorMessage);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 扩图失败: " + errorMessage);
            }

            return createOutPaintingTaskResponse;
        }

    }


    /**
     * 查询任务状态结果
     * @param taskId 任务 ID
     * @return  任务响应
     */
    public GetOutPaintingTaskResponse getOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(taskId == null, ErrorCode.OPERATION_ERROR, "任务 ID 不能为空");
        // 发起请求
        // curl --location --request GET 'https://dashscope.aliyuncs.com/api/v1/tasks/{task_id}' \
        //--header "Authorization: Bearer $DASHSCOPE_API_KEY"
        HttpRequest httpRequest = HttpRequest.get(String.format(GET_OUT_PAINTING_TASK_URL, taskId))
                .header("Authorization", "Bearer " + apiKey)
                // 不设置，AI扩图的时候会报SSL连接错误
                .setSSLProtocol("TLSv1.2");

        // 处理异常
        String url = String.format(GET_OUT_PAINTING_TASK_URL, taskId);
        try (HttpResponse httpResponse = httpRequest.get(url)
                .header("Authorization", "Bearer " + apiKey)
                .execute()) {
            if (!httpResponse.isOk()) {
                log.error("请求异常: {}", httpResponse.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取任务结果失败");
            }
            return JSONUtil.toBean(httpResponse.body(), GetOutPaintingTaskResponse.class);
        }
    }
}
