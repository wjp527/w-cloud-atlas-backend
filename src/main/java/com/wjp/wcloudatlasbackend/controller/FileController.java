package com.wjp.wcloudatlasbackend.controller;

import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.wjp.wcloudatlasbackend.annotation.AuthCheck;
import com.wjp.wcloudatlasbackend.common.BaseResponse;
import com.wjp.wcloudatlasbackend.common.ResultUtils;
import com.wjp.wcloudatlasbackend.constant.UserConstant;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.manager.CosManager;
import com.wjp.wcloudatlasbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * 文件接口
 *
 * @author <a href="https://github.com/liwjp">程序员鱼皮</a>
 * @from <a href="https://wjp.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    // COS管理器
    @Resource
    private CosManager cosManager;



    /**
     * 测试上传文件
     * @param multipartFile 文件
     * @RequestPart("file"): 上传的文件参数名称
     */
    @PostMapping("/test/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<String> testUploadFile(@RequestPart("file") MultipartFile multipartFile) {
        // 文件目录
        String filename = multipartFile.getOriginalFilename();
        String filepath = String.format("/test/%s", filename);
        File file = null;
        try {
          // 上传文件
          // 创建临时文件
          file = File.createTempFile(filepath, null);
          // 保存文件到临时文件(就是将用户传来的图片资源保存到file里面)
          multipartFile.transferTo(file);
          // 上传到Cos
          cosManager.putObject(filepath, file);
          // 返回可访问的地址
          return ResultUtils.success(filepath);
        } catch(Exception e) {
           log.error("file upload error, filepath = " + filepath, e);
           throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 删除临时文件
            if(file != null) {
                boolean delete = file.delete();
                if(!delete) {
                    log.error("delete temp file error, filepath = " + filepath);
                }
            }
        }
    }

    /**
     * 测试下载文件
     * @param filepath 文件路径
     * @param response HTTP响应
     * @throws IOException
     */
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/test/download")
    public void testDownloadFile(String filepath, HttpServletResponse response) throws IOException {
        // 获取文件
        COSObjectInputStream cosObjectInput = null;
        // 创建 GetObjectRequest 对象，设置存储桶名称、ObjectKey
        COSObject cosObject = cosManager.getObject(filepath);
        // 获取 COSObject 的输入流
        cosObjectInput = cosObject.getObjectContent();
        try {
            // 处理下载的流
            byte[] bytes = IOUtils.toByteArray(cosObjectInput);
            // 设置响应头
            // 流式响应
            // application/octet-stream: 二进制流数据。对于大多数浏览器，这种类型会触发下载行为
            response.setContentType("application/octet-stream;charset=UTF-8");
            // 设置HTTP响应的文件名
            // setHeader(): 用于设置HTTP响应头部字段
            // Content-Disposition:
            // - HTTP头字段，用于指定响应的呈现方式
            // - attachment: 响应一个附件，客户端会下载文件
            // - filename= : 制定文件的下载名称
            //   - filepath: 是下载到客户端显示的名称
            response.setHeader("Content-Disposition", "attachment; filename=" + filepath);

            // 写入响应
            response.getOutputStream().write(bytes);
            // 刷新缓冲区
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("file download error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载失败");
        } finally {
            // ✨关闭流
            if(cosObjectInput != null) {
                cosObjectInput.close();
            }
        }

    }
}
