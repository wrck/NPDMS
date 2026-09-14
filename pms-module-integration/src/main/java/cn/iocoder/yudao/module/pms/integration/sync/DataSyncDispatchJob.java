package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataSyncDispatchJob implements JobHandler {
    private final SyncTaskMapper tasks;
    private final SyncRunService runner;
    @Override @TenantJob
    public String execute(String param) {
        runner.maintain();
        int accepted=0;
        for(var task:tasks.selectDue(new SyncQueries.Due(SyncTaskService.tenant(),LocalDateTime.now()))) {
            boolean full=task.getNextFullAt()!=null&&!task.getNextFullAt().isAfter(LocalDateTime.now());
            String request="schedule_"+task.getId()+"_"+(full?task.getNextFullAt():task.getNextRunAt())
                    .toString().replaceAll("[^0-9]","");
            try {
                var definition=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
                Long retryOf=task.getRetryAttempt()>0&&task.getRetryAttempt()<=definition.retryCount()?task.getLastFailedRunId():null;
                runner.start(new SyncRunService.Start(task.getId(),task.getVersion(),request,false,full,false,retryOf,false));
                accepted++;
            }catch(IllegalArgumentException ex){ /* Another manual/scheduled request may already own this task. */ }
        }
        return "已提交 "+accepted+" 个同步任务；业务结果请查看集成运行记录";
    }
}
