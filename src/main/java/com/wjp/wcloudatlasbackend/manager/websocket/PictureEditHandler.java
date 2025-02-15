package com.wjp.wcloudatlasbackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.wjp.wcloudatlasbackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.wjp.wcloudatlasbackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.wjp.wcloudatlasbackend.manager.websocket.model.PictureEditRequestMessage;
import com.wjp.wcloudatlasbackend.manager.websocket.model.PictureEditResponseMessage;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片编辑 WebSocket 处理器
 * @author wjp
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    /**
     * Disruptor 事件生产者
     */
    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    // 这里使用 并发的HashMap，是为了后续的线程安全问题
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();


    /**
     * 连接建立成功
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        // 保存会话到集合中
        User user =  (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 加入到集合中
        // putIfAbsent: 当前这个会话还没有用户加入，需要进行初始化
        // ConcurrentHashMap.newKeySet(): 创建一个线程安全的集合, 这个集合的类型是 Set，可以用于存储唯一值
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        // 确保每个图片 ID 对应的会话集合以线程安全的方式存储
        pictureSessions.get(pictureId).add(session);
        // 构造响应，发送加入编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 广播给所有用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);

    }

    /**
     * 收到前端发送的消息，根据消息类别处理消息
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected
    void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);

        // 获取消息内容，将 JSON 转换为 PictureEditRequestMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);

        // 从 Session 属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        // 根据消息类型处理消息 (生产消息到 Disruptor 环形队列中)
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    /**
     * 进入编辑状态
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 没有用户正在编辑该图片，才能进入编辑
        if(!pictureEditingUsers.containsKey(pictureId)) {
            // 设置用户正在编辑图片
            pictureEditingUsers.put(pictureId, user.getId());
            // 构造响应，发送编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s 进入编辑", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 广播消息
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }


    /**
     * 处理编辑操作
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {

        // 正在编辑的用户
        // pictureEditingUsers
        // key: pictureId, value: 当前正在编辑的用户 ID
        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 获取到编辑的具体操作
        String editAction = pictureEditRequestMessage.getEditAction();
        if(editAction == null) {
            log.error("编辑操作为空");
            return;
        }

        // 确定是当前的编辑者
        if(editingUserId != null && editingUserId.equals(user.getId())) {
            // 构造响应,发送具体操作的通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s 执行 %s 操作", user.getUserName(), editAction);
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 发送通知（不包括发消息方）
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);

        }

    }

    /**
     * 退出编辑操作
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 判断用户是否正在编辑该图片
        Long editingUserId = pictureEditingUsers.get(pictureId);

        // 判断该登录用户 是否是 即将要退出编辑图片的用户
        if(editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除用户正在编辑该图片
            pictureEditingUsers.remove(pictureId);

            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s 退出编辑", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 发送消息
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }


    /**
     * 连接关闭
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);

        // 从 Session 属性中获取公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        // 移除当前用户对某张图片的编辑状态。
        handleExitEditMessage(null, session, user,pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);

        // {
        //  "pictureSessions": {
        //    "1": {
        //      "sessionSet": [
        //        {
        //          "sessionId": "session1",
        //          "userId": "user1",
        //          "attributes": {
        //            "user": {
        //              "id": 1,
        //              "userName": "User1"
        //            },
        //            "pictureId": 1
        //          },
        //          "isOpen": true
        //        },
        //      ]
        //    }
        // }
        if(sessionSet != null) {
            // 移除当前会话
            sessionSet.remove(session);
            // 如果 sessionSet 为空，那么就要移除 pictureSessions 对应下图片id的数据
            if(sessionSet.isEmpty()) {
                // 集合为空，移除图片 ID 对应的会话集合
                pictureSessions.remove(pictureId);
            }
        }

        // 通知其他用户，该用户已经离开编辑
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s 离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 发送消息
        broadcastToPicture(pictureId, pictureEditResponseMessage);


    }


    /**
     * 广播给图片的所有用户（支持排除某个 Session）
     * @param pictureId 	                要广播的目标图片 ID
     * @param pictureEditResponseMessage    需要广播的消息对象
     * @param excludeSession                排除的用户会话
     * @throws IOException
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws IOException {
        // 获取图片的所有会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if(CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化: 将 Long 类型 转为 String 类型，解决精度丢失问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);

            // 将消息对象序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            // 创建 TextMessage 对象
            TextMessage textMessage = new TextMessage(message);
            // 遍历会话集合，发送消息
            for (WebSocketSession session : sessionSet) {
                // 排除掉 本人，不给本人发送消息，防止操作多次执行
                if(excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if(session.isOpen()) {
                    // 发送消息
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    /**
     * 广播给图片的所有用户（不支持排除某个 Session）
     * @param pictureId
     * @param pictureEditResponseMessage
     * @throws IOException
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws IOException {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }
}
