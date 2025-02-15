package com.wjp.wcloudatlasbackend.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.wjp.wcloudatlasbackend.manager.auth.SpaceUserAuthManager;
import com.wjp.wcloudatlasbackend.manager.auth.model.SpaceUserPermissionConstant;
import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.entity.domain.Space;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.model.enums.SpaceTypeEnum;
import com.wjp.wcloudatlasbackend.service.PictureService;
import com.wjp.wcloudatlasbackend.service.SpaceService;
import com.wjp.wcloudatlasbackend.service.SpaceUserService;
import com.wjp.wcloudatlasbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 拦截器，建立连接前要先校验
 * @author wjp
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {
    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserService spaceUserService;

    // 权限管理器
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 建立连接前要先校验
     * @param request
     * @param response
     * @param wsHandler
     * @param attributes 给 WebSocket 的 Session 会话设置属性
     * @return
     * @throws Exception
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 获取当前登录用户
        if(request instanceof ServletServerHttpRequest) {
            // 从一个请求对象中获取底层的HTTP请求对象，以便能够访问HTTP请求中的详细信息（如请求头、请求参数、请求主体等）
            HttpServletRequest httpServletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 从请求中获取参数
            String pictureId = httpServletRequest.getParameter("pictureId");
            if(StrUtil.isBlank(pictureId)) {
                log.error("缺少图片参数，拒绝握手");
                return false;
            }

            // 获取当前用户登录
            User loginUser = userService.getLoginUser(httpServletRequest);
            if(ObjUtil.isEmpty(loginUser)) {
                log.error("用户未登录，拒绝握手");
                return false;
            }

            // 校验用户是否有编辑当前图片的权限
            Picture picture = pictureService.getById(pictureId);
            if(ObjUtil.isEmpty(picture)) {
                log.error("图片不存在，拒绝握手");
                return false;
            }

            // 如果是团队空间，并且是编辑者权限，才能建立连接
            Long spaceId = picture.getSpaceId();
            Space space = null;
            // 有空间 id
            if(spaceId != null) {
                // 根据 空间id 查询 对应图片的空间信息
                space = spaceService.getById(spaceId);
                if(ObjUtil.isEmpty(space)) {
                    log.error("空间不存在，拒绝握手");
                    return false;
                }
                // 如果 该空间的类型 不是 团队空间，则拒绝握手
                if(space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
                    log.error("图片空间不是团队空间，拒绝握手");
                    return false;
                }
            }

            // 校验用户是否有编辑当前图片的权限
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            // 查看他是否有图片编辑的权限
            if(!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                log.error("用户没有编辑图片的权限，拒绝握手");
                return false;
            }

            // 设置用户登录信息等属性到 WebSocket 会话中
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("pictureId", Long.valueOf(pictureId)); // 记得转换为 Long 类型
        }

        return true;
    }

    /**
     * 建立连接后要做的事情
     * @param request
     * @param response
     * @param wsHandler
     * @param exception
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
