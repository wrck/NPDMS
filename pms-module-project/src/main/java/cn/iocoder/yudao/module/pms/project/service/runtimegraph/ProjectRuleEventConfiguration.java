package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ProjectRuleEventConfiguration {
    /** Bounded immediate workers; overflow stays in Outbox instead of blocking committed business requests. */
    @Bean(defaultCandidate = false)
    public ThreadPoolTaskExecutor projectRuleEventExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("project-rule-event-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(128);
        return executor;
    }
}
