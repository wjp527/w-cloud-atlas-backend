package com.wjp.wcloudatlasbackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.wjp.wcloudatlasbackend.manager.websocket.model.PictureEditRequestMessage;
import com.wjp.wcloudatlasbackend.model.entity.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 图编辑事件 生产者
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    // 获取到Disruptor
    // 就是 在这里定义的: @Bean("pictureEditEventDisruptor")
    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发布事件
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        // 获取到RingBuffer
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 获取到可以防止事件的位置
        long next = ringBuffer.next();
        // 获取到事件对象
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        // 设置事件对象属性
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        // 设置事件对象属性
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);

        // 发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void destroy() {
        pictureEditEventDisruptor.shutdown();
    }

}
































