package com.wjp.wcloudatlasbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureReviewRequest;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureUploadByBatchRequest;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureUploadRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author wjp
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-01-19 16:56:42
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     * @param inputSource
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

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

    /**
     * 验证图片
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 审核图片
     * @param pictureReviewRequest 审核请求
     * @param loginUser            登录用户
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);


    /**
     * 批量抓取和创建图片
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                 User loginUser);


    /**
     * 百度图片批量抓取
     * @return
     */
    Integer PictureBatchForBaiDu(String searchText, String namePrefix, String category, List<String> tags, Integer page, Integer count, User loginUser);

    /**
     * Bing图片批量抓取
     * @return
     */
    Integer PictureBatchForBing(String searchText, String namePrefix, String category, List<String> tags,Integer page, Integer count, User loginUser);


    /**
     * 清理 cos 中的图片文件
     * @param oldPicture
     */
    void clearPictureFile(Picture oldPicture);


}
