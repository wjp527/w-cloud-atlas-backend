package com.wjp.wcloudatlasbackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
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
}
