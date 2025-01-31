package com.wjp.wcloudatlasbackend.api.imagesearch.sub;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wjp.wcloudatlasbackend.api.imagesearch.model.ImageSearchResult;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 获取图片列表
 * @author wjp
 */
@Slf4j
public class GetImageListApi {

    public static List<ImageSearchResult> getImageList(String url) {
        try {
            // 发起 GET 请求
            HttpResponse response = HttpUtil.createGet(url).execute();

            // 获取响应结果
            int statusCode = response.getStatus();
            String body = response.body();

            // 处理异常
            if(statusCode == 200) {
                // 解析 JSON 数据并处理
                return processResponse(body);
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }

        } catch(Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片列表失败");
        }
    }

    /**
     * 处理接口响应内容
     * @param responseBody 接口响应内容
     * @return 图片列表
     */
    public static List<ImageSearchResult> processResponse(String responseBody) {
        // 解析响应结果
        JSONObject jsonObject = new JSONObject(responseBody);
        if(!jsonObject.containsKey("data")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取图片列表");
        }

        JSONObject data = jsonObject.getJSONObject("data");

        if(!data.containsKey("list")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
        JSONArray list = data.getJSONArray("list");
        return JSONUtil.toList(list, ImageSearchResult.class);
    }


    public static void main(String[] args) {
        String url = "https://graph.baidu.com/ajax/pcsimi?carousel=503&entrance=GENERAL&extUiData%5BisLogoShow%5D=1&inspire=general_pc&limit=30&next=2&render_type=card&session_id=8328517542964920283&sign=126d0e97cd54acd88139901738227358&tk=5bb82&tpl_from=pc";
        List<ImageSearchResult> imageList = getImageList(url);
        System.out.println("搜索成功: " + imageList);
    }
}
