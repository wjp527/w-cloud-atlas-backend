package com.wjp.wcloudatlasbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.mapper.SpaceMapper;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceAddRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceQueryRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.analyze.*;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.SpaceLevelEnum;
import com.wjp.wcloudatlasbackend.model.vo.space.SpaceVO;
import com.wjp.wcloudatlasbackend.model.vo.space.analyze.*;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import com.wjp.wcloudatlasbackend.service.PictureService;
import com.wjp.wcloudatlasbackend.service.SpaceAnalyzeService;
import com.wjp.wcloudatlasbackend.service.SpaceService;
import com.wjp.wcloudatlasbackend.service.UserService;
import org.apache.catalina.users.AbstractUser;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
* @author wjp
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-01-25 16:35:44
*/
@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceAnalyzeService {

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;


    /**
     * 获取空间使用情况分析
     * @param spaceUsageAnalyzeRequest 空间资源使用分析
     * @param loginUser 登录用户
     * @return 空间资源使用分析结果
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        // 校验权限
        ThrowUtils.throwIf(spaceUsageAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");

        // 如果是 全部图库 或者 公共图库
        if(spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            // 仅管理员可以访问
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);

            // 统计公共图库的资源使用
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            // 只查询 图片大小 的字段
            queryWrapper.select("picSize");
            // 补充查询范围
            // ✨优化空间，只返回 图片大小 字段
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);
            // 最后会收集到 所有图片 picSize 字段，并用list整合
            // pictureService.getBaseMapper(): 是不是可以理解为 picture这个数据表
            // getBaseMapper().selectObjs(): 他是用于只进行查询一个字段的，并返回List<Object>
            // selectObjs(queryWrapper): 就是在这个 picture数据表中 查询 picSize字段，并返回一个list
            List<Object> pictureObjList  = pictureService.getBaseMapper().selectObjs(queryWrapper);

            // mapToLong(): 将 Object流 转为  Long流，这样会方便后续的计算操作
            long usedSize = pictureObjList.stream().mapToLong(obj -> (Long) obj).sum();
            long useCount = pictureObjList.size();
            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            // 设置当前使用的空间大小
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            // 设置当前使用的空间数量
            spaceUsageAnalyzeResponse.setUsedCount(useCount);
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);

            return spaceUsageAnalyzeResponse;
        } else {
            // 私有图库
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间ID不能为空");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

            // 仅管理员 和 空间创建人 可以访问
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);

            // 返回 spaceId 对应的 空间使用情况分析结果
            Long totalSize = space.getTotalSize();
            Long totalCount = space.getTotalCount();
            Long maxSize = space.getMaxSize();
            Long maxCount = space.getMaxCount();
            // 计算比例
            double sizeUsageRatio = NumberUtil.round(totalSize * 100.0 / maxSize, 2).doubleValue();
            double countUsageRatio = NumberUtil.round(totalCount * 100.0 / maxCount, 2).doubleValue();

            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(totalSize);
            spaceUsageAnalyzeResponse.setUsedCount(totalCount);
            spaceUsageAnalyzeResponse.setMaxSize(maxSize);
            spaceUsageAnalyzeResponse.setMaxCount(maxCount);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(sizeUsageRatio);
            spaceUsageAnalyzeResponse.setCountUsageRatio(countUsageRatio);

            return spaceUsageAnalyzeResponse;
        }
    }

    /**
     * 获取空间分析
     * @param spaceCategoryAnalyzeRequest 空间分析请求
     * @param loginUser 登录用户
     * @return 空间分析结果
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        // 校验权限
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        // 检查权限
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);

        // 使用 MyBatis Plus 分页查询
        // 将category相同的数据，都进行计算对应的个数，和 picSize的总和。最终用grouby进行分组
        // [{totalSize=523284, count=1, category=插画}, {totalSize=126890, count=2, category=海报}]
        queryWrapper.select("category AS category", "count(*) as count", "sum(picSize) as totalSize")
                .groupBy("category");

        // 查询并转换结果
        // getBaseMapper().selectMaps(): 用于查询多个字段的，并返回List<Map<String, Object>>
        // [{totalSize=523284, count=1, category=插画}, {totalSize=126890, count=2, category=海报}]
        return pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result -> {
                    // 处理空值，默认值为 "未分类"
                    String category = result.get("category") != null ? result.get("category").toString() : "未分类";
                    // 类型安全转换
                    // count 和 totalSize 字段的处理更加健壮
                    // 先将结果转换为 Number 类型，(Integer、Long 都继承自 Number)
                    // 然后调用 longValue() 方法转为 Long类型
                    // 避免了 result.get("count") == null 的问题
                    Long count = ((Number) result.get("count")).longValue();
                    Long totalSize = ((Number) result.get("totalSize")).longValue();
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                })
                .collect(Collectors.toList());


    }

    /**
     * 获取空间标签分析
     * @param spaceTagAnalyzeRequest 空间分析请求
     * @param loginUser      登录用户
     * @return 空间标签分析结果
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 检查权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();

        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);

        // 查询符合条件的标签
        // 只需要查询 tags 字段
        queryWrapper.select("tags");
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                // 过滤掉 tags 为 null 和 "" 的情况
                .filter(ObjectUtil::isNotEmpty)
                // 这里 pictureService.getBaseMapper().selectObjs(queryWrapper) 返回的结果是 List<Object>
                // 所以我们需要转换为 String 类型，才能使用 JSONUtil.toList() 方法
                .map(Object::toString)
                .collect(Collectors.toList());

        // 解析标签并统计
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                // 实现 扁平化
                // ["生活"]，["生活","创意"] -> ["生活","生活","创意"]
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        // 转换为响应对象，按照使用次数进行排序
        System.out.println("===" + tagCountMap.entrySet());
        // tagCountMap.entrySet()
        // 将对象表示一个标签和其出现次数的键值对 [生活=2, 创意=1]
        List<SpaceTagAnalyzeResponse> spaceTagAnalyzeResponses = tagCountMap.entrySet().stream()
                // 降序排序
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                // 生成响应对象
                .map(e -> new SpaceTagAnalyzeResponse(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return spaceTagAnalyzeResponses;
    }

    /**
     * 获取空间图片大小分析
     * @param spaceSizeAnalyzeRequest 空间查询请求
     * @param loginUser 登录用户
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 检查权限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("picSize");

        // 100、500、1000
        List<Long> picSizeList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .map(size -> (Long) size)
                .collect(Collectors.toList());

        // 定义分段范围，注意使用有序的 Map
        LinkedHashMap<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizeList.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizeList.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizeList.stream().filter(size -> size >= 500 * 1024 && size < 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizeList.stream().filter(size -> size >= 1024 * 1024).count());

        // 转换为响应对象
        // sizeRanges.entrySet(): [<100KB=59, 100KB-500KB=6, 500KB-1MB=2, >1MB=0]
        List<SpaceSizeAnalyzeResponse> spaceSizeAnalyzeResponses = sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return spaceSizeAnalyzeResponses;


    }

    /**
     * 获取用户上传行为分析
     * @param spaceUserAnalyzeRequest 用户上传行为分析请求
     * @param loginUser 登录用户
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 检查权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        // 构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);

        // 补充用户id 查询
        Long userId = spaceUserAnalyzeRequest.getUserId();
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);

        // 补充分析维度: 每日、每周 、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "daily":
                queryWrapper.select("date_format(createTime, '%Y-%m-%d') as period", "count(*) as count");
                break;
            case "week":
                // TODO: 有问题
//                queryWrapper.select("date_format(createTime, '%Y-%u') as period", "count(*) as count");
                queryWrapper.select("YEARWEEK(createTime) as period", "count(*) as count");
                break;
            case "month":
                queryWrapper.select("date_format(createTime, '%Y-%m') as period", "count(*) as count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }

        // 分组排序
        queryWrapper.groupBy("period").orderByAsc("period");

        // 查询并封装返回结果
        List<Map<String, Object>> queryResult = pictureService.getBaseMapper().selectMaps(queryWrapper);
        List<SpaceUserAnalyzeResponse> analyzeResponses = queryResult.stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    // 类型安全转换
                    // count 字段的处理更加健壮
                    // 先将结果转换为 Number 类型，(Integer、Long 都继承自 Number)
                    // 然后调用 longValue() 方法转为 Long类型
                    // 避免了 result.get("count") == null 的问题
                    Long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());

        return analyzeResponses;
    }


    /**
     * 获取空间使用排行分析
     * @param spaceRankAnalyzeRequest 空间使用排行分析请求
     * @param loginUser 登录用户
     * @return 空间使用排行分析结果
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");


        // 检查权限，仅管理员可以查看
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("limit " + spaceRankAnalyzeRequest.getTopN()); // 只取前N条

        List<Space> list = spaceService.list(queryWrapper);

        return list;

    }


    /**
     * 检查空间分析权限
     * @param spaceAnalyzeRequest 请求体
     * @param loginUser 登录用户
     */
    public void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        // 校验参数
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();

        // 全空间分析或者公共图库权限校验，仅管理员可访问
        if(queryAll || queryPublic) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权限访问该空间");
        } else {
            // 获取空间id
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            // 校验参数
            ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR, "空间ID不能为空");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验空间权限【是管理员还是空间创建人】
            spaceService.checkSpaceAuth(loginUser,space);
        }
    }

    /**
     * 根据请求对象封装查询条件
     * @param spaceAnalyzeRequest 请求对象
     * @param queryWrapper  查询条件封装器
     */
    private void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        // 全空间分析
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        if(queryAll) {
            return ;
        }

        // 公共图库分析
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        if(queryPublic) {
            queryWrapper.isNull("spaceId");
            return ;
        }

        // 分析特定空间
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if(spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return ;
        }

        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }


}




