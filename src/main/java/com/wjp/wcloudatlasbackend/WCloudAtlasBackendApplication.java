package com.wjp.wcloudatlasbackend;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class}) // 禁用sharding-sphere自动配置
// 开启异步
@EnableAsync
// 扫描mapper包
@MapperScan("com.wjp.wcloudatlasbackend.mapper")
// 开启aop功能
@EnableAspectJAutoProxy(exposeProxy = true)
public class WCloudAtlasBackendApplication {

    public static void main(String[] args) {

        SpringApplication.run(WCloudAtlasBackendApplication.class, args);


    }

}
