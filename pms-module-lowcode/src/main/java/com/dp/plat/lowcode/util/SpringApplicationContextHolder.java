package com.dp.plat.lowcode.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文持有器。
 *
 * <p>供非 Spring 管理的对象（如由 Quartz 实例化的 Job）获取 Spring Bean 使用。</p>
 */
@Component
public class SpringApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringApplicationContextHolder.applicationContext = applicationContext;
    }

    public static <T> T getBean(Class<T> requiredType) {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化");
        }
        return applicationContext.getBean(requiredType);
    }

    public static Object getBean(String name) {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 尚未初始化");
        }
        return applicationContext.getBean(name);
    }
}
