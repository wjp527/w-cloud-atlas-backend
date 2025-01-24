package com.wjp.wcloudatlasbackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.wjp.wcloudatlasbackend.config.CosClientConfig;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.manager.CosManager;
import com.wjp.wcloudatlasbackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 图片上传模版
 * @author wjp
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 处理输入源 (本地文件 或 URL)
     * @param inputSource
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     * @param inputSource
     * @return
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     * @param inputSource
     * @param file
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;


    /**
     * 上传图片
     * @param inputSource 图片文件
     * @param uploadPathPrefix 上传路径前缀
     * @return 返回 图片的详细信息 包括 图片的URL、名称、大小、宽度、高度、缩放比例、格式
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 校验图片
        validPicture(inputSource);

        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginFilename(inputSource);
        // 文件名: 日期_uuid.后缀
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, FileUtil.getSuffix(originFilename));
        String uploadPath = String .format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;

        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            // 处理输入源文件，假设 processFile 方法对文件进行了某种处理，比如格式转换、优化等
            processFile(inputSource, file);
            // 上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取上传图片的基本信息，获取原始图片的图像信息（如尺寸、格式等）
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 获取图片处理结果，假设处理操作会返回一个结果列表
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            // 获取处理结果中的对象列表，如果列表不为空，则表示图片已成功处理
            List<CIObject> objectList = processResults.getObjectList();
            if(CollUtil.isNotEmpty(objectList)) {
                // 获取压缩后的图片对象，这里假设列表的第一个元素是压缩后的图片
                CIObject compressedObject = objectList.get(0);
                // 封装压缩图，返回结果
                return buildResult(originFilename, compressedObject);
            }
            // 如果没有处理过的图片（即没有压缩图），直接返回上传的原始图片信息
            return buildResult(originFilename, uploadPath, file, imageInfo);
        } catch(Exception e) {
            log.error("图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传失败");
        } finally {
            // 删除临时文件
            this.deleteTempFile(file);
        }
    }

    private UploadPictureResult buildResult(String originFilename, CIObject compressedObject) {
        // 封装返回结果
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedObject.getWidth();
        int picHeight = compressedObject.getHeight();
        // 计算图片缩放比例
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        // compressedObject.getKey(): 压缩图的路径
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedObject.getKey());
        uploadPictureResult.setPicName(FileUtil.getName(originFilename));
        uploadPictureResult.setPicSize(compressedObject.getSize().longValue());
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedObject.getFormat());

        return uploadPictureResult;
    }


    /**
     * 构建返回结果
     * @param originFilename
     * @param uploadPath
     * @param file
     * @param imageInfo
     * @return
     */
    private UploadPictureResult buildResult(String originFilename, String uploadPath, File file, ImageInfo imageInfo) {
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
