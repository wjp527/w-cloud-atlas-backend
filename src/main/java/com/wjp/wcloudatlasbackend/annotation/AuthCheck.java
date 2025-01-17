package com.wjp.wcloudatlasbackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验
 * @author wjp
 */
// 注解的生效范围
@Target(ElementType.METHOD)
// 注解的生命周期
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须具有某个角色
     * @return
     */
    String mustRole() default "";

}
