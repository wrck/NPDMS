package cn.iocoder.yudao.module.pms.bindings.framework;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Configuration;

/** 事件驱动后端的后台轮询调度开关；随模块装配，不依赖宿主应用额外开启。 */
@Configuration
@EnableScheduling
public class BindingsSchedulingConfiguration {
}
