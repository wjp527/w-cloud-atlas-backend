package com.wjp.wcloudatlasbackend.manager.websocket.disruptor;

import cn.hutool.json.JSONUtil;
import com.lmax.disruptor.WorkHandler;
import com.wjp.wcloudatlasbackend.manager.websocket.PictureEditHandler;
import com.wjp.wcloudatlasbackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.wjp.wcloudatlasbackend.manager.websocket.model.PictureEditRequestMessage;
import com.wjp.wcloudatlasbackend.manager.websocket.model.PictureEditResponseMessage;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import com.wjp.wcloudatlasbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * 事件处理器 (消费者)
 * @author wjp
 */
@Component
@Slf4j
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private UserService userService;

    /**
     * 处理事件
     * @param pictureEditEvent 事件对象
     * @throws Exception     异常
     */
    @Override
    public void onEvent(PictureEditEvent pictureEditEvent) throws Exception {
        // 获取到事件对象
        PictureEditRequestMessage pictureEditRequestMessage = pictureEditEvent.getPictureEditRequestMessage();
        // 获取到事件对象
        WebSocketSession session = pictureEditEvent.getSession();

        User user = pictureEditEvent.getUser();
        Long pictureId = pictureEditEvent.getPictureId();

        // 获取到消息类别
        String type = pictureEditRequestMessage.getType();
        // 根据消息类型获取到枚举
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);


        // 根据消息类型处理消息
        switch(pictureEditMessageTypeEnum) {
            case ENTER_EDIT:
                // 进入编辑状态
                pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EXIT_EDIT:
                // 退出编辑状态
                pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EDIT_ACTION:
                // 编辑操作
                pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            default:
                // 其他消息类型，返回错误提示
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                // 发送消息
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));

                break;
        }
    }
}
