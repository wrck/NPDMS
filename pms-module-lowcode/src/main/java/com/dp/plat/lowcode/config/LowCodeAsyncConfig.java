package com.dp.plat.lowcode.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 低代码模块异步任务配置（缺口3）。
 *
 * <p>启用 Spring {@code @Async} 支持，使 {@code LowCodeImportAsyncProcessor}
 * 的异步导入方法在独立线程池中执行，不阻塞 HTTP 请求线程。</p>
 *
 * <p>使用 Spring 默认 {@code SimpleAsyncTaskExecutor}（每次调用新建线程），
 * 适用于低代码数据导入这种低频操作。若后续高并发场景需要线程池复用，
 * 可在此处定义 {@code ThreadPoolTaskExecutor} Bean。</p>
 */
@Configuration
@EnableAsync
public class LowCodeAsyncConfig {
}
