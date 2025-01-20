package com.wjp.wcloudatlasbackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.wjp.wcloudatlasbackend.config.CosClientConfig;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class FileManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;


    /**
     * 上传图片
     * @param multipartFile 图片文件
     * @param uploadPathPrefix 上传路径前缀
     * @return 返回 图片的详细信息 包括 图片的URL、名称、大小、宽度、高度、缩放比例、格式
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 校验图片
        validPicture(multipartFile);

        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originFilename = multipartFile.getOriginalFilename();
        // 文件名: 日期_uuid.后缀
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originFilename));
        String uploadPath = String .format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;

        try {
           // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);
            // 上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            // 计算图片缩放比例
            double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
            uploadPictureResult.setPicName(FileUtil.getName(originFilename));
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());

            return uploadPictureResult;
        } catch(Exception e) {
            log.error("图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            // 删除临时文件
            this.deleteTempFile(file);
        }
    }


    /**
     * 校验图片
     * @param multipartFile
     */
    public void validPicture(MultipartFile multipartFile) {
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
     * 删除临时文件
     * @param file
     */
    public void deleteTempFile(File file) {
        if(file == null) {
            return ;
        }
        // 删除临时文件
        boolean deleteResult = file.delete();
        if(!deleteResult) {
            log.error("delete temp file error, filepath = " + file.getAbsolutePath());
        }
    }


}
