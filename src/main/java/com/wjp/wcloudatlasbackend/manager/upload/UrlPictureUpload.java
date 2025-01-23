package com.wjp.wcloudatlasbackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * URL图片上传
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    /**
     * 处理输入源 (URL路径)
     * @param inputSource
     */
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR,"文件地址不能为空");

        try {
            // 1. 校验 URL 格式
            new URL(fileUrl);
        } catch(MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件格式不正确");
        }

        // 2. 校验 URL协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")), ErrorCode.PARAMS_ERROR, "仅支持HTTP 或 HTTPS 协议的文件地址");

        // 3. 发送 HEAD 请求，以及验证文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl)
                    .execute();
            // 未正常返回，无需执行其他判断
            if(response.getStatus() != HttpStatus.HTTP_OK) {
                return ;
            }
            // 4. 校验文件类型
            String contentType = response.header("Content-Type");
            if(StrUtil.isNotBlank(contentType)) {
                // 运行的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType), ErrorCode.PARAMS_ERROR, "不支持的文件类型");
            }

            // 5. 校验文件大小
            String contentLengthStr = response.header("Content-Length");
            if(StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long TWO_M = 2 * 1024 * 1024; // 限制文件大小为2MB
                    ThrowUtils.throwIf(contentLength > TWO_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过2MB");
                } catch(NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式不正确");
                }
            }
        } finally {
            // 释放资源
            if(response != null) {
                response.close();
            }
        }
    }

    /**
     * 获取输入源的原始文件名
     * @param inputSource
     * @return
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 鱼皮写法
//        String originalFilename = FileUtil.mainName(fileUrl);
        URL url = null;

        try {
            url = new URL(fileUrl);
            fileUrl = url.getFile().substring(url.getFile().lastIndexOf("/"));
        } catch(MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件格式不正确");
        }

        return fileUrl;
    }

    /**
     * 处理输入源并生成本地临时文件
     * @param inputSource
     * @param file
     * @throws Exception
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 下载文件
        HttpUtil.downloadFile(fileUrl, file);
    }
}
