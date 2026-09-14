package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

/** Framework owns asynchronous launch and execution metadata.
 * https://docs.spring.io/spring-batch/reference/job/configuring-operator.html */
@Configuration(proxyBeanMethods=false)
@EnableBatchProcessing(taskExecutorRef="syncBatchExecutor")
@EnableJdbcJobRepository(tablePrefix="int_batch_")
public class SyncBatchConfiguration {
    @Bean
    public ThreadPoolTaskExecutor syncBatchExecutor() {
        var executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);executor.setMaxPoolSize(2);executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("data-sync-");executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
    @Bean
    public Job dataSyncJob(JobRepository repository,PlatformTransactionManager transactionManager,SyncRunService runner) {
        var attribute=new DefaultTransactionAttribute();
        attribute.setPropagationBehavior(DefaultTransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        var step=new StepBuilder("synchronizeBusinessSnapshot",repository)
                .tasklet((contribution,context)->{
                    var parameters=context.getStepContext().getStepExecution().getJobParameters();
                    TenantUtils.execute(parameters.getLong("tenantId"),()->{
                        runner.execute(parameters.getLong("runId"));return null;
                    });
                    return RepeatStatus.FINISHED;
                },transactionManager).transactionAttribute(attribute).build();
        return new JobBuilder("dataSyncJob",repository).start(step).build();
    }
}

