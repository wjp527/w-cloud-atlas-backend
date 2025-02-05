package com.wjp.wcloudatlasbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.exception.ThrowUtils;
import com.wjp.wcloudatlasbackend.mapper.SpaceUserMapper;
import com.wjp.wcloudatlasbackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.wjp.wcloudatlasbackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.SpaceUser;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.SpaceRoleEnum;
import com.wjp.wcloudatlasbackend.model.vo.space.SpaceVO;
import com.wjp.wcloudatlasbackend.model.vo.spaceuser.SpaceUserVO;
import com.wjp.wcloudatlasbackend.model.vo.user.UserVO;
import com.wjp.wcloudatlasbackend.service.SpaceService;
import com.wjp.wcloudatlasbackend.service.SpaceUserService;
import com.wjp.wcloudatlasbackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author wjp
* @description 针对表【spaceUser_user(空间用户关联)】的数据库操作Service实现
* @createDate 2025-02-04 15:19:53
*/
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
    implements SpaceUserService {

    @Resource
    private UserService userService;

    @Resource
    // 循环依赖，懒加载
    @Lazy
    private SpaceService spaceService;


    /**
     * 事务模板
     */
    @Resource
    private TransactionTemplate transactionTemplate;
    /**
     * 校验空间是否合法 【add = true 新增，false 修改】
     * @param spaceUser
     * @param add
     */
    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR, "空间不能为空");

        // 从对象中取值
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();

        // 要创建
        if(add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR, "空间或用户不能为空");
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }

        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if(spaceRoleEnum == null && spaceRole != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }


    }

    /**
     * 获取空间包装类(单条)
     * @param spaceUser
     * @param request
     * @return
     */
    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request) {
        // 对象 => 封装类
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);

        // 查询相关用户信息
        Long userId = spaceUserVO.getUserId();
        if(userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceUserVO.setUser(userVO);
        }

        // 查询相关空间信息
        Long spaceId = spaceUser.getSpaceId();
        if(spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
            spaceUserVO.setSpace(spaceVO);
        }
        return spaceUserVO;
    }


    /**
     * 获取空间包装类(列表)
     * @param spaceUserList
     * @return
     */
    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {

        // 判断输入列表是否为空
        if(CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }

        // 对象列表 => 封装类列表
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream()
                // 将 spaceUserList 中的每个 spaceUser 对象，转换为 SpaceUserVO 对象
                .map(SpaceUserVO::objToVo)
                .collect(Collectors.toList());


        // 1.关联查询用户信息
        Set<Long> userIdSet = spaceUserList.stream()
                // 将 spaceUserList 中的每个 userId 取出来，放到 map 中
                .map(SpaceUser::getUserId)
                // 整合 为  Set
                .collect(Collectors.toSet());

        // 关联查询空间信息
        Set<Long> spaceIdSet = spaceUserList.stream()
                .map(SpaceUser::getSpaceId)
                .collect(Collectors.toSet());

        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 1 => space1, 2 => space2
        Map<Long, List<Space>> spaceIdSpaceListMap = spaceService.listByIds(spaceIdSet).stream()
                .collect(Collectors.groupingBy(Space::getId));

        // 2. 填充信息
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            User user = null;

            // 检查 Map 中是否包含指定的键
            if(userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceUserVO.setUser(userService.getUserVO(user));

            Long spaceId = spaceUserVO.getSpaceId();
            Space space = null;
            // 检查 Map 中是否包含指定的键
            if(spaceIdSpaceListMap.containsKey(spaceId)) {
                space = spaceIdSpaceListMap.get(spaceId).get(0);
            }
//            spaceUserVO.setSpace(spaceService.getSpaceVO(space, request));
            spaceUserVO.setSpace(SpaceVO.objToVo(space));
        });

        return spaceUserVOList;
    }


    /**
     * 获取查询条件【整合查询的SQL语句】
     * @param spaceUserQueryRequest 查询条件
     * @return
     */
    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper queryWrapper = new QueryWrapper();
        if(spaceUserQueryRequest == null) {
            return  queryWrapper;
        }

        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();

        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);

        return queryWrapper;
    }

    /**
     * 创建空间
     * @param spaceUserAddRequest 请求体
     * @return 创建的空间id
     */
    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR, "请求体不能为空");

        // 封装对象
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);

        validSpaceUser(spaceUser, true);

        // 保存
        boolean save = this.save(spaceUser);
        if(!save) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存失败");
        }

        return spaceUser.getId();

    }

}




