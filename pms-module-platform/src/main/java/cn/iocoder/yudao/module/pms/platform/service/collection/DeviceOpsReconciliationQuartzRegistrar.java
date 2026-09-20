package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.infra.api.job.JobApi;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsGatewayApi;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 网关装配后才同步对账任务到 Quartz，避免网关未启用时校验失败的启动阻断。
 *
 * infra_job 种子由 V255 迁移维护；迁移未执行或种子被禁用时只告警跳过，
 * 不阻断应用启动，后续补齐迁移并重启即可恢复轮询。
 */
@Component
@ConditionalOnBean(DeviceOpsGatewayApi.class)
@Slf4j
public class DeviceOpsReconciliationQuartzRegistrar implements ApplicationRunner {

    static final String HANDLER_NAME = "deviceOpsReconciliationJob";

    private final JobApi jobApi;
    private final ObjectProvider<Scheduler> schedulerProvider;

    public DeviceOpsReconciliationQuartzRegistrar(JobApi jobApi, ObjectProvider<Scheduler> schedulerProvider) {
        this.jobApi = jobApi;
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProvider.getIfAvailable() == null) {
            return;
        }
        try {
            jobApi.syncEnabledJobByHandlerName(HANDLER_NAME);
        } catch (RuntimeException exception) {
            log.warn("DAC 对账任务种子缺失或未启用（V255 迁移未执行），跳过 Quartz 同步：{}", exception.getMessage());
        }
    }
}
