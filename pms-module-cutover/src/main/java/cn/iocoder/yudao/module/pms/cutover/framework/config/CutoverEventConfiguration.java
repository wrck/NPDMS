package cn.iocoder.yudao.module.pms.cutover.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class CutoverEventConfiguration {
    @Bean
    ThreadPoolTaskExecutor cutoverEventExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(2); executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("cutover-result-");
        return executor;
    }
}
