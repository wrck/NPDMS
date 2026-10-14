package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class CutoverRuntimeConfiguration {
    @Bean
    CutoverRuntimeTaskApplicationService cutoverTaskApplicationService(CutoverTaskMapper taskMapper,
            CutoverTaskDeviceScopeMapper deviceMapper, CutoverTaskStageHistoryMapper historyMapper,
            CutoverAssessmentMapper assessmentMapper, CutoverConfigurationRevisionMapper configurationMapper,
            CutoverProjectScopePort projectScopePort, CutoverProjectContextPort projectContextPort,
            CutoverDeviceScopePort deviceScopePort, CutoverDeviceProductTypePort deviceProductTypePort,
            CutoverCustomerLevelPort customerLevelPort, CutoverReadinessPort readinessPort,
            PlatformCommandExecutionApi commandExecutionApi, cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy intakeConfiguration) {
        return new CutoverRuntimeTaskApplicationService(taskMapper, deviceMapper, historyMapper, assessmentMapper,
                configurationMapper, projectScopePort, projectContextPort, deviceScopePort, deviceProductTypePort,
                customerLevelPort, readinessPort, commandExecutionApi, Clock.systemDefaultZone(), intakeConfiguration);
    }

    @Bean
    CutoverRuntimeTaskQueryService cutoverTaskQueryService(CutoverTaskMapper taskMapper, CutoverTaskDeviceScopeMapper deviceMapper,
            CutoverAssessmentMapper assessmentMapper,
            CutoverChecklistMapper checklistMapper, CutoverConfigurationRevisionMapper configurationMapper,
            CutoverProjectScopePort projectScopePort, CutoverProjectContextPort projectContextPort,
            CutoverDeviceScopePort deviceScopePort, CutoverCustomerLevelPort customerLevelPort,
            CutoverReadinessPort readinessPort, CutoverFrozenDeviceMapper frozenDevices, CutoverSelectedTaskMapper selectedTasks,
            cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience approvalAudience) {
        return new CutoverRuntimeTaskQueryService(taskMapper, deviceMapper, assessmentMapper, checklistMapper,
                configurationMapper, projectScopePort, projectContextPort, deviceScopePort, customerLevelPort,
                readinessPort, Clock.systemDefaultZone(), frozenDevices, selectedTasks, approvalAudience);
    }

    @Bean
    CutoverRuntimeTaskRequestCodec cutoverRuntimeTaskRequestCodec(ObjectMapper mapper) {
        return new CutoverRuntimeTaskRequestCodec(mapper);
    }

    @Bean
    CutoverTaskRequestContext cutoverTaskRequestContext(PermissionApi permissions) {
        return () -> {
            Long actor = CutoverOwnerAdapters.actor();
            return new CutoverTaskContext(CutoverOwnerAdapters.tenant(), actor,
                    UUID.randomUUID().toString(),
                    permissions.hasAnyPermissions(actor, "pms:cutover-task:save-assessment"),
                    permissions.hasAnyPermissions(actor, "pms:cutover-task:submit-assessment"),
                    permissions.hasAnyPermissions(actor, "pms:cutover-task:save-checklist"),
                    permissions.hasAnyPermissions(actor, "pms:cutover-task:request-collection"),
                    permissions.hasAnyPermissions(actor, "pms:cutover-task:submit-checklist"));
        };
    }
}
