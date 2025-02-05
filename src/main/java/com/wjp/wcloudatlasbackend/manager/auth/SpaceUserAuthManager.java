package com.wjp.wcloudatlasbackend.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.wjp.wcloudatlasbackend.manager.auth.model.SpaceUserAuthConfig;
import com.wjp.wcloudatlasbackend.manager.auth.model.SpaceUserPermissionConstant;
import com.wjp.wcloudatlasbackend.manager.auth.model.SpaceUserRole;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.SpaceUser;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.SpaceRoleEnum;
import com.wjp.wcloudatlasbackend.model.enums.SpaceTypeEnum;
import com.wjp.wcloudatlasbackend.service.SpaceUserService;
import com.wjp.wcloudatlasbackend.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 空间成员权限管理
 * @author wjp
 */
@Component
public class SpaceUserAuthManager {

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

    // 空间成员权限配置
    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    // 静态代码块初始化
    static{
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    /**
     * 根据角色后去权限列表
     * @param spaceUserRole 角色
     * @return 权限列表
     */

    public List<String> getPermissionsByRole(String spaceUserRole) {
        if(StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles()
                .stream()
                .filter(r -> r.getKey().equals(spaceUserRole))
                .findFirst()
                .orElse(null);

        if(role == null) {
            return new ArrayList<>();
        }

        return role.getPermissions();
    }


    /**
     * 获取对应用户的权限列表
     * @param space
     * @param loginUser
     * @return
     */
    public List<String> getPermissionList(Space space, User loginUser) {
        if(loginUser == null) {
            return new ArrayList<>();
        }

        // 管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());

        // 公共图库
        if(space == null) {
            if(userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
        }

        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        // 给出的空间类型不符合要求
        if(spaceTypeEnum == null) {
            return new ArrayList<>();
        }

        // 根据空间获取对应的权限
        switch(spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所权限
                if(space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间，查询 SpaceUser 并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, space.getId())
                        .eq(SpaceUser::getUserId, space.getUserId())
                        .one();

                if(spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    return getPermissionsByRole(spaceUser.getSpaceRole());
                }
        }
        return new ArrayList<>();


    }


























}
