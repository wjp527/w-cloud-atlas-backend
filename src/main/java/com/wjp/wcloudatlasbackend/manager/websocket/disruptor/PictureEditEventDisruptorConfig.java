package com.wjp.wcloudatlasbackend.manager.websocket.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.concurrent.ThreadFactory;

/**
 * 图片编辑事件 Disruptor 配置
 * @author wjp
 */
@Configuration
public class PictureEditEventDisruptorConfig {

    // 注入消费者
    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    // 创建 disruptor
    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> messageModelRingBuffer() {
        // 定义 ringBuffer(环形缓冲区) 的大小
        int bufferSize = 1024 * 256;
        // 创建 disruptor
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                // 定义事件工厂
                PictureEditEvent::new,
                // 定义 ringBuffer 大小
                bufferSize,
                // 定义线程工厂
                ThreadFactoryBuilder.create()
                        // 设置线程名称前缀
                        .setNamePrefix("pictureEditEventDisruptor")
                        .build()
        );

        // 设置消费者
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        // 启动 disruptor
        disruptor.start();

        return disruptor;
    }

}
