package cn.iocoder.yudao.module.pms.integration.sync;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SyncBatchLauncher {
    private final Job dataSyncJob;
    private final JobOperator operator;
    private final JobRepository repository;
    public Long launch(Long tenantId,Long runId) {
        var parameters=parameters(tenantId,runId);
        var existing=repository.getLastJobExecution("dataSyncJob",parameters);
        if(existing!=null)return existing.getId();
        try {return operator.start(dataSyncJob,parameters).getId();}
        catch(Exception ex){throw new IllegalStateException("批处理框架启动失败",ex);}
    }
    public void recover(Long tenantId,Long runId) {
        var existing=repository.getLastJobExecution("dataSyncJob",parameters(tenantId,runId));
        if(existing!=null && existing.getStatus().isRunning()) operator.recover(existing);
    }
    private static JobParameters parameters(Long tenantId,Long runId){
        return new JobParametersBuilder().addLong("tenantId",tenantId).addLong("runId",runId).toJobParameters();
    }
}

