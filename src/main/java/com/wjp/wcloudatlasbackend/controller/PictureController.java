package com.wjp.wcloudatlasbackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.utils.IOUtils;
import com.wjp.wcloudatlasbackend.annotation.AuthCheck;
import com.wjp.wcloudatlasbackend.api.imagesearch.ImageSearchApiFacade;
import com.wjp.wcloudatlasbackend.api.imagesearch.model.ImageSearchResult;
import com.wjp.wcloudatlasbackend.common.BaseResponse;
import com.wjp.wcloudatlasbackend.common.DeleteRequest;
import com.wjp.wcloudatlasbackend.common.ResultUtils;
import com.wjp.wcloudatlasbackend.constant.UserConstant;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.manager.CosManager;
import com.wjp.wcloudatlasbackend.manager.PictureCacheManager;
import com.wjp.wcloudatlasbackend.model.dto.picture.*;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceLevel;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.PictureReviewStatusEnum;
import com.wjp.wcloudatlasbackend.model.enums.SpaceLevelEnum;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureTagCategory;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureVO;
import com.wjp.wcloudatlasbackend.service.PictureService;
import com.wjp.wcloudatlasbackend.service.SpaceService;
import com.wjp.wcloudatlasbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 图片接口
 *
 * @author <a href="https://github.com/liwjp">程序员鱼皮</a>
 * @from <a href="https://wjp.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    /**
     * 引入 Redis 操作对象
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 构造本地缓存 (Caffeine)
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            // 定义初始容量
            .initialCapacity(1024)
            // 定义最大容量
            .maximumSize(10_000)
            // 定义过期时间
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();


    @Resource
    private PictureCacheManager pictureCacheManager;

    @Resource
    private SpaceService spaceService;


    /**
     * 通过本地文件上传图片(可重新上传)
     * @param multipartFile 图片文件
     * @param pictureUploadRequest 图片上传请求
     * @param response 响应
     * @param request 请求
     * @return 图片信息
     */
    @PostMapping("/upload")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile multipartFile,
                                                 PictureUploadRequest pictureUploadRequest,
                                                 HttpServletResponse response,
                                                 HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);

    }

    /**
     * 通过 URL上传图片(可重新上传)
     * @param pictureUploadRequest
     * @param response
     * @param request
     * @return
     */
    @PostMapping("/upload/url")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPictureByUrl(@RequestBody PictureUploadRequest pictureUploadRequest,
                                                 HttpServletResponse response,
                                                 HttpServletRequest request) {
        String fileUrl = pictureUploadRequest.getFileUrl();
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);

    }

    /**
     * 删除图片
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 校验参数
        if(deleteRequest == null || (deleteRequest.getId() == null && deleteRequest.getIds() == null )) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        // 单个图片id
        String pictureId = deleteRequest.getId();

        // 批量图片id
        List<String> pictureIds = deleteRequest.getIds();
        // 删除图片
        pictureService.deletePicture(pictureId, pictureIds, loginUser);

        return ResultUtils.success(true);

    }



    /**
     * 更新图片信息（仅管理员可用）
     * @param pictureUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        // 检验参数
        if(pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        // 将实体类 和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(picture.getTags()));
        // 数据校验
        pictureService.validPicture(picture);

        // 判断是否存在
        Long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);

        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新失败");

        return ResultUtils.success(true);
    }

    /**
     * 获取图片信息
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "参数为空");

        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片 (封装类)
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "参数为空");

        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");


        // 空间权限校验
        Long spaceId = picture.getSpaceId();
        if(spaceId != null) {
            User loginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(loginUser, picture);
        }

        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 分页获取图片列表(仅管理员可见)
     * @param pictureQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        // 查询数据库
        // 根据 查询条件进行 分页
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));

        return ResultUtils.success(picturePage);

    }


    /**
     * 分页获取图片列表(封装类) 【多条数据】
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        // 限制爬虫
       ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);


        Long spaceId = pictureQueryRequest.getSpaceId();
        if(spaceId == null) {
            // 公开图库
            // ✨普通用户 只查找 审核通过的 数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null ,   ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if(!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }

        // 查询数据库
        // 根据 查询条件进行 分页
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        // 封装成 VO
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);



        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 分页获取图片列表(封装类) 【多条数据】
     * ✨使用 Caffeine + Redis 多级缓存 【目前最优解】
     * 解决问题:
     *  - 缓存雪崩
     *  - 缓存击穿
     *  - 缓存穿透
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/cache/manager")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCacheManager(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {

        Long spaceId = pictureQueryRequest.getSpaceId();
        if(spaceId == null) {
            // 公开图库
            // ✨普通用户 只查找 审核通过的 数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null ,   ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if(!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }

        Page<PictureVO> pagePictureCache = pictureCacheManager.getPagePictureCache(pictureQueryRequest, request);
        return ResultUtils.success(pagePictureCache);
    }

    /**
     * 测试缓存雪崩
     * @param request
     * @return
     */
    @GetMapping("/cache/avalanche")
    public BaseResponse<Boolean> CacheAvalanche(HttpServletRequest request) throws InterruptedException {
        int threadCount = 50;  // 模拟50个并发请求
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    PictureQueryRequest pictureQueryRequest = new PictureQueryRequest();
                    // 设置查询条件
                    pictureQueryRequest.setCurrent(1);
                    pictureQueryRequest.setPageSize(10);

                    pictureCacheManager.getPagePictureCache(pictureQueryRequest,request);  // 触发缓存查询
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();  // 等待所有线程完成

        return ResultUtils.success(true);
    }

        /**
         * 多级 缓存 (Redis + Caffeine)
         * 分页获取图片列表(封装类，有缓存) 【多条数据】
         * @param pictureQueryRequest
         * @param request
         * @return
         */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);

        // ✨普通用户 只查找 审核通过的 数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());


        // 构建缓存 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        // 将获取到的 请求参数 转换成 字符串，并且进行 md5 加密，作为缓存的 key
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        // caffeine key
        String caffeineKey = String.format("listPictureVOByPage:%s", hashKey);
        // 整合 redis key
        String redisKey = String.format("wCloudAtlas:listPictureVOByPage:%s", hashKey);

        // Caffeine 本地缓存
        // 根据 caffeine key 获取 缓存数据
        String cachedValue = LOCAL_CACHE.getIfPresent(caffeineKey);
        // 本地缓存有值
        if(cachedValue != null) {
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);

            return ResultUtils.success(cachedPage);
        }

        // Redis 缓存
        // 查询缓存，缓存中有，就直接取出，如果没有则查询数据库，并写入缓存中
        // 获取 redis 操作对象
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        // 根据 redis key 获取 缓存数据
        cachedValue = opsForValue.get(redisKey);

        // 有缓存
        if(cachedValue != null) {
            // 缓存数据转换成 Page 对象
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 如果 本地缓存 和 Redis 都没有命中缓存
        // 查询数据库
        // 根据 查询条件进行 分页
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        // 封装成 VO
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 没有缓存，查询数据库，并写入缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 存入缓存
        // Caffeine 存入本地缓存
        LOCAL_CACHE.put(caffeineKey, cacheValue);

        // Redis 存入 Redis 缓存
        // 设置过期时间, 5-10分钟，随机的，防止雪崩
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        opsForValue.set(redisKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * Redis 缓存
     * 分页获取图片列表(封装类，有缓存) 【多条数据】
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/redis")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithRedis(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);

        // ✨普通用户 只查找 审核通过的 数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());


        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        // 姜获取到的 请求参数 转换成 字符串，并且进行 md5 加密，作为缓存的 key
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        // 整合 redis key
        String redisKey = String.format("wCloudAtlas:listPictureVOByPage:%s", hashKey);

        // 查询缓存，缓存中有，就直接取出，如果没有则查询数据库，并写入缓存中
        // 获取 redis 操作对象
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        // 根据 redis key 获取 缓存数据
        String cachedValue = opsForValue.get(redisKey);

        // 有缓存
        if(cachedValue != null) {
            // 缓存数据转换成 Page 对象
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 查询数据库
        // 根据 查询条件进行 分页
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        // 封装成 VO
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 没有缓存，查询数据库，并写入缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 设置过期时间, 5-10分钟，随机的，防止雪崩
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        // 存入缓存
        opsForValue.set(redisKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * Caffeine 本地缓存
     * 分页获取图片列表(封装类，有缓存) 【多条数据】
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/caffeine")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCaffeine(@RequestBody PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);

        // ✨普通用户 只查找 审核通过的 数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());


        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        // 姜获取到的 请求参数 转换成 字符串，并且进行 md5 加密，作为缓存的 key
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        // 整合 redis key
        String cacheKey = String.format("listPictureVOByPage:%s", hashKey);

        // 查询缓存，缓存中有，就直接取出，如果没有则查询数据库，并写入缓存中
        // 根据 caffeine key 获取 缓存数据
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);

        // 有缓存
        if(cachedValue != null) {
            // 缓存数据转换成 Page 对象
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 查询数据库
        // 根据 查询条件进行 分页
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(pictureQueryRequest));
        // 封装成 VO
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 没有缓存，查询数据库，并写入缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 存入缓存
        LOCAL_CACHE.put(cacheKey, cacheValue);

        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片信息（普通用户）
     * @param pictureEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {

        if(pictureEditRequest == null || pictureEditRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        // 编辑图片信息
        pictureService.editPicture(pictureEditRequest, loginUser);

        return ResultUtils.success(true);

    }


    /**
     * 获取图片标签分类
     * @param request
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> pictureTagCategory(HttpServletRequest request) {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "艺术", "校园", "创意", "运动", "科技", "其他");
        List<String> categoryList = Arrays.asList("模版", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        
        return ResultUtils.success(pictureTagCategory);
    }


    /**
     * 图片审核(管理员可用)
     * @param pictureReviewRequest
     * @param request
     * @return
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {

        ThrowUtils.throwIf(pictureReviewRequest == null , ErrorCode.PARAMS_ERROR, "参数为空");

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");

        // 图片审核
        pictureService.doPictureReview(pictureReviewRequest, loginUser);

        return ResultUtils.success(true);
    }


    /**
     * 批量上传图片(管理员可用)
     * @param pictureUploadByBatchRequest
     * @param request
     * @return
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR, "参数为空");
        User loginUser = userService.getLoginUser(request);
        Integer uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }


    /**
     * 获取空间等级列表
     * @return
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()
                )).collect(Collectors.toList());

        return ResultUtils.success(spaceLevelList);
    }


    /**
     * 以图搜图
     * @return
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        // 校验参数
        ThrowUtils.throwIf(searchPictureByPictureRequest == null , ErrorCode.PARAMS_ERROR, "参数为空");
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片ID为空");
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null , ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 调用图搜图接口
        List<ImageSearchResult> imageSearchResults = ImageSearchApiFacade.searchImage(picture.getThumbnailUrl());
        return ResultUtils.success(imageSearchResults);
    }


}
