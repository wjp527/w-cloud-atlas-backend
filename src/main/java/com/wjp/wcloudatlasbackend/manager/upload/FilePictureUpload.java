package com.wjp.wcloudatlasbackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 本地文件图片上传
 * @author wjp
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate {
    /**
     * 处理输入源 (本地文件)
     * @param inputSource
     */
    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        // 图片不能为空
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "图片不能为空");

        // 1. 校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "图片大小不能超过2M");

        // 2. 校验文件后缀
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许上传的文件后缀
        final List<String> ALLOW_SUFFIX_LIST = Arrays.asList("jpeg", "jpg","png","webp");
        ThrowUtils.throwIf(!ALLOW_SUFFIX_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "不支持的文件类型");
    }

    /**
     * 获取输入源的原始文件名
     * @param inputSource
     * @return
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        String originFilename = multipartFile.getOriginalFilename();
        return originFilename;
    }

    /**
     * 处理输入源并生成本地临时文件
     * @param inputSource
     * @param file
     * @throws Exception
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }


}
