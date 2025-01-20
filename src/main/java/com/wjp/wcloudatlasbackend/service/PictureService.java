package com.wjp.wcloudatlasbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureUploadRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author wjp
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-01-19 16:56:42
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 获取查询结果【封装类】 单条
     * @param picture
     * @param request
     * @return
     */

    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取分页查询结果【封装类】 多条
     * @param picturePage
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 获取查询对象
     * @param pictureQueryRequest   查询条件
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    void validPicture(Picture picture);
}
