package com.wjp.wcloudatlasbackend.service.impl;

import ch.qos.logback.classic.spi.EventArgUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wjp.wcloudatlasbackend.constant.UserConstant;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceAddRequest;
import com.wjp.wcloudatlasbackend.model.dto.space.SpaceQueryRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.SpaceUser;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.SpaceLevelEnum;
import com.wjp.wcloudatlasbackend.model.enums.SpaceRoleEnum;
import com.wjp.wcloudatlasbackend.model.enums.SpaceTypeEnum;
import com.wjp.wcloudatlasbackend.model.vo.space.SpaceVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import com.wjp.wcloudatlasbackend.service.SpaceService;
import com.wjp.wcloudatlasbackend.mapper.SpaceMapper;
import com.wjp.wcloudatlasbackend.service.SpaceUserService;
import com.wjp.wcloudatlasbackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author wjp
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-01-25 16:35:44
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    /**
     * 事务模板
     */
    @Resource
    private TransactionTemplate transactionTemplate;
    /**
     * 校验空间是否合法 【add = true 新增，false 修改】
     * @param space
     * @param add
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR, "空间不能为空");

        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        // 根据 枚举值 取出 对应的 枚举对象
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);

        // 空间类型
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);

        // 要创建
        if(add) {
            if(StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }

            if(spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间等级不能为空");
            }

            if(spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不能为空");
            }
        }

        // 要修改
        if(spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间等级错误");
        }

        if(spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型错误");
        }

        if(StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

    /**
     * 获取空间包装类(单条)
     * @param space
     * @param request
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象 => 封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);

        // 查询相关用户信息
        Long userId = spaceVO.getUserId();
        if(userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }


    /**
     * 获取空间包装类(分页)
     * @param spacePage
     * @param request
     * @return
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {

        // 获取分页数据
        List<Space> spaceList = spacePage.getRecords();
        long current = spacePage.getCurrent();
        long pageSize = spacePage.getSize();
        long total = spacePage.getTotal();
        Page<SpaceVO> spaceVOPage = new Page<>(current, pageSize, total);
        if(CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }

        // 对象列表 => 封装类列表
        List<SpaceVO> spaceVOList = spaceList.stream()
                // 将 spaceList 中的每个 space 对象，转换为 SpaceVO 对象
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());


        // 1.关联查询用户信息
        Set<Long> userIdSet = spaceList.stream()
                // 将 spaceList 中的每个 userId 取出来，放到 map 中
                .map(Space::getUserId)
                // 整合 为  Set
                .collect(Collectors.toSet());

        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            // 检查 Map 中是否包含指定的键
            if(userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });

        spaceVOPage.setRecords(spaceVOList);

        return spaceVOPage;
    }


    /**
     * 获取查询条件【整合查询的SQL语句】
     * @param spaceQueryRequest 查询条件
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        if(spaceQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "查询条件不能为空");
        }

        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Integer spaceType = spaceQueryRequest.getSpaceType();

        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = new QueryWrapper();

        queryWrapper.eq(ObjectUtil.isNotNull(id), "id", id);
        queryWrapper.eq(ObjectUtil.isNotNull(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.like(ObjectUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.like(ObjectUtil.isNotEmpty(spaceType), "spaceType", spaceType);


        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);

        return queryWrapper;
    }

    /**
     * 创建空间
     * @param spaceAddRequest 请求体
     * @param loginUser 登录用户
     * @return 创建的空间id
     */
    @Override
    @Transactional
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1. 填充参数默认值
        // 转换实体类 和 DTO
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        if(StrUtil.isBlank(space.getSpaceName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
        }
        // 如果没有传递 spaceLevel，默认为普通空间
        if(space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }

        // 如果没有传递 spaceType，默认为私有空间
        if(space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 根据 spaceLevelEnum 获取对应的最大容量和最大数量
        this.fillSpaceBySpaceLevel(space);

        // 2. 校验参数
        this.validSpace(space, true);

        // 3. 校验权限，非管理员整你创建普通级别的空间
        if(loginUser.getId() == null || loginUser.getId() <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if(!userService.isAdmin(loginUser)) {
            if(space.getSpaceLevel() != SpaceLevelEnum.COMMON.getValue()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "非管理员不能创建非普通空间");
            }
        }



        // 4. 控制同一个用户只能创建一个私有空间
        String lock = String.valueOf(userId).intern();
        synchronized(lock) {
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 检查是否存在私有空间
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getSpaceType, space.getSpaceType())
                        .exists();

                // 已创建国私有空间，是不能再次创建的
                if (exists) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "每个用户每类空间只能创建一个");
                }

                // 创建私有空间
                boolean save = this.save(space);
                ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "创建空间失败");



                // 创建成功后，如果是团队空间，关联新增团队成员记录
                if(space.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                    // 创建团队成员记录
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());

                    save = spaceUserService.save(spaceUser);

                    ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "创建空间成员记录失败");

                }

                // 返回 已创建好的私有空间id
                return space.getId();
            });
            return newSpaceId;

        }

    }


    /**
     * 根据空间级别填充空间对象
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {

        // 校验空间是否合法
        if(space == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间不能为空");
        }

        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        if(spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间等级错误");
        }

        // 查询出 对应 空间类别的 最大数量 和 最大容量
        long maxCount = spaceLevelEnum.getMaxCount();
        long maxSize = spaceLevelEnum.getMaxSize();

        Long newMaxCount = space.getMaxCount();
        Long newMaxSize = space.getMaxSize();

        // 如果管理员没有设置 最大值 和 最大容量, 则使用 默认值
        if(newMaxCount == null) {
            space.setMaxCount(maxCount);
        }

        if(newMaxSize == null) {
            space.setMaxSize(maxSize);
        }
    }

    /**
     * 校验空间权限
     * @param loginUser 登录用户
     * @param space 空间对象
     */
    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        // 权限校验
        // 如果不是管理员并且也不是空间的本人，则校验不通过
        if(!UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole()) && !loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }
    }


}




