package cn.iocoder.yudao.module.pms.integration.sync;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

class SyncBatchFrameworkTest {
    @Test void nativeBatchPersistsSuccessfulAndFailedExecutions() throws Exception {
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            var resource=new ClassPathResource("org/springframework/batch/core/schema-h2.sql");
            String sql=new String(resource.getInputStream().readAllBytes(),StandardCharsets.UTF_8).replace("BATCH_","int_batch_");
            new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)))
                    .execute(context.getBean(DataSource.class));
            var operator=context.getBean(JobOperator.class);var job=context.getBean(Job.class);
            var repository=context.getBean(JobRepository.class);var runner=context.getBean(SyncRunService.class);
            doThrow(new IllegalArgumentException("test failure")).when(runner).execute(2L);
            for(long runId: new long[]{1L,2L}) {
                var parameters=new JobParametersBuilder().addLong("tenantId",1L).addLong("runId",runId).toJobParameters();
                var execution=operator.start(job,parameters);
                await().atMost(Duration.ofSeconds(10)).until(()->!repository.getJobExecution(execution.getId()).getStatus().isRunning());
                var done=repository.getJobExecution(execution.getId());
                assertEquals(runId==1L?"COMPLETED":"FAILED",done.getStatus().name());
            }
            verify(runner).execute(1L);verify(runner).execute(2L);
        }
    }
    @Configuration(proxyBeanMethods=false)
    @Import(SyncBatchConfiguration.class)
    static class Config {
        @Bean DataSource dataSource(){return new DriverManagerDataSource("jdbc:h2:mem:batch_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");}
        @Bean PlatformTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean SyncRunService runner(){return mock(SyncRunService.class);}
    }
}

