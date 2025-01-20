package com.wjp.wcloudatlasbackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.wjp.wcloudatlasbackend.config.CosClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

/**
 * Cos 对象存储操作
 *
 * @author <a href="https://github.com/liwjp">程序员鱼皮</a>
 * @from <a href="https://wjp.icu">编程导航知识星球</a>
 */
@Component
@Slf4j
public class CosManager {
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传文件到 COS 对象存储
     * @param key 文件要存储到存储桶的位置 【唯一键】
     * @param file 文件对象
     * @return  上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        // 创建 PutObjectRequest 对象，设置存储桶名称、文件输入流、ObjectKey
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        // 进行上传文件对象到 COS 对象存储
        return cosClient.putObject(putObjectRequest);

    }


    /**
     * 下载 COS 对象存储的文件
     * @param key 文件在 COS 对象存储的位置 【唯一键】
     * @return
     */
    public COSObject getObject(String key){
        // 创建 GetObjectRequest 对象，设置存储桶名称、ObjectKey
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        // 进行下载 COS 对象存储的文件
        COSObject cosObject = cosClient.getObject(getObjectRequest);
        return cosObject;

    }


    /**
     * 上传图片到 COS 对象存储【附带图片信息】 数据万象
     * @param key
     * @param file
     * @return
     */
    public PutObjectResult putPictureObject(String key,File file) {
        // 创建 PutObjectRequest 对象，设置存储桶名称、文件输入流、ObjectKey
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        // 创建 PicOperations 对象，用于设置图片处理操作
        PicOperations picOperations = new PicOperations();

        // 设置是否返回图片信息
        // 参数说明：
        // - 1: 表示返回图片信息
        // - 0: 表示不返回图片信息
        picOperations.setIsPicInfo(1);

        // 将图片处理操作设置到上传请求中
        putObjectRequest.setPicOperations(picOperations);

        // 调用 COS 客户端的 putObject 方法，执行上传操作
        // 返回 PutObjectResult 对象，包含上传操作的结果信息
        return cosClient.putObject(putObjectRequest);
    }




}
