package com.wjp.wcloudatlasbackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.wjp.wcloudatlasbackend.exception.BusinessException;
import com.wjp.wcloudatlasbackend.exception.ErrorCode;
import com.wjp.wcloudatlasbackend.manager.auth.model.SpaceUserPermissionConstant;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.SpaceUser;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.SpaceRoleEnum;
import com.wjp.wcloudatlasbackend.model.enums.SpaceTypeEnum;
import com.wjp.wcloudatlasbackend.service.PictureService;
import com.wjp.wcloudatlasbackend.service.SpaceService;
import com.wjp.wcloudatlasbackend.service.SpaceUserService;
import com.wjp.wcloudatlasbackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.wjp.wcloudatlasbackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    // 默认是: /api
    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 返回一个账号所拥有的权限码集合
     * @param loginId 账号唯一标识
     * @param loginType 登录类型，如："space"、"default"
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {

        // 1.判断 loginType，仅对类型为 "space" 进行权限校验
        if(!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        //2.管理员权限，表示权限校验通过
        List<String> ADMIN_PERMISSION = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());

        //3.获取上下文对象：从请求中获取 spaceuserauthcontext 上下文
        SpaceUserAuthContext authContext = getAuthContextByRequest();

        // 如果所有字段都为空，表示查询公共图库，可以通过
        if(isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSION;
        }


        // 4.获取登录用户信息：从 StpKit 中获取当前登录用户信息
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if(loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId();

        //5.从上下文中优先获取spaceuser对象：如果上下文中存在 spaceuser对象，直接根据其角色获取权限码列表。
        SpaceUser spaceUser = authContext.getSpaceUser();
        if(spaceUser != null) {
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        //6.通过 spaceuserid获取空间用户信息：如果上下文中存在 spaceuserid:
        Long spaceUserId = authContext.getSpaceUserId();
        if(spaceUserId != null) {
            spaceUser = spaceUserService.getById(spaceUserId);
            if(spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 取出当前登录用户赌赢的spaceUser
            // sql语句:
            // SELECT *
            // FROM space_user
            // WHERE space_id = {spaceUser.getSpaceId()}
            // AND user_id = {userId}
            //LIMIT 1;
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();

            if(loginSpaceUser == null) {
                return new ArrayList<>();
            }

            // 这里会导致管理员在私有空间没有权限，可以再查一次库处理
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 如果没有 spaceUserId，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
        Long spaceId = authContext.getSpaceId();
        if(spaceId == null) {
            // 如果没有 spaceId，通过 pictureId 获取 Picture 对象和 Space对象
            Long pictureId = authContext.getPictureId();

            // 图片 id 也灭有，则默认通过权限检验
            if(pictureId == null) {
                return ADMIN_PERMISSION;
            }

            // sql: SELECT id, space_id, user_id
            //      FROM picture
            //      WHERE id = #{pictureId}
            //      LIMIT 1;
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();


            if(picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }

            spaceId = picture.getSpaceId();

            // 公共图库，仅本人后管理员可操作
            if(spaceId == null) {
                if(picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSION;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }

        // 获取 space 对象
        Space space = spaceService.getById(spaceId);
        if(space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }

        // 根据 Space 类型判断权限
        if(space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间
            if(space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSION;
            } else {
                return new ArrayList<>();
            }
        } else {
            // 团队空间，查询 SpaceUser 并获取角色和权限
            spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();

            if(spaceUser == null) {
                return new ArrayList<>();
            }

            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }


    /**
     * 获取请求参数或请求体中的 SpaceUserAuthContext 对象
     * @return
     */

    private SpaceUserAuthContext getAuthContextByRequest() {
        // RequestContextHolder: 获取当前请求的 RequestAttributes 对象，从中获取 Request 对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        // 获取请求头中的 Content-Type
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        // 解析请求参数
        SpaceUserAuthContext authRequest;

        // 兼容 get 或 post 操作
        if(ContentType.JSON.getValue().equals(contentType)) {
            // 获取请求体
            String body = ServletUtil.getBody(request);
            // 转换为对象
            authRequest =  JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            // 获取请求参数
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            // 转换为对象
            authRequest =  BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }

        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();

        if(ObjectUtil.isNotNull(id)) {
            // 根据请求路径区分模块名称
            String requestUri = request.getRequestURI();
            // 截取请求路径 api/space/1 => space/1
            String partUri = requestUri.replace(contextPath + "/", "");
            // 截取模块名称 space/1 => space
            String moduleName = StrUtil.subBefore(partUri, "/", false);

            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                default:
                    break;
            }
        }
        return authRequest;

    }


    /**
     * 判断对象是否所有字段都为空
     * @param object 对象
     * @return true：所有字段都为空，false：有非空字段
     */
    private boolean isAllFieldsNull(Object object) {
        if(object == null) {
            return true; // 对象为空
        }

        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }

}
