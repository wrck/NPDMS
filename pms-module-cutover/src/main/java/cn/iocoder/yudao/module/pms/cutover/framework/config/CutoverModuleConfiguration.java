package cn.iocoder.yudao.module.pms.cutover.framework.config;

import cn.iocoder.yudao.module.pms.cutover.controller.business.task.CutoverTaskRequestCodec;
import cn.iocoder.yudao.module.pms.cutover.controller.business.task.CutoverTaskRequestContext;
import cn.iocoder.yudao.module.pms.cutover.framework.adapter.CutoverOwnerAdapters;

import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.CutoverChecklistMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.service.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class CutoverModuleConfiguration {
    @Bean
    CutoverTaskApplicationService cutoverTaskApplicationService(CutoverTaskMapper taskMapper,
            CutoverTaskDeviceScopeMapper deviceMapper, CutoverTaskStageHistoryMapper historyMapper,
            CutoverAssessmentMapper assessmentMapper, CutoverConfigurationRevisionMapper configurationMapper,
            CutoverProjectScopePort projectScopePort, CutoverProjectContextPort projectContextPort,
            CutoverDeviceScopePort deviceScopePort, CutoverDeviceProductTypePort deviceProductTypePort,
            CutoverCustomerLevelPort customerLevelPort, CutoverReadinessPort readinessPort,
            PlatformCommandExecutionApi commandExecutionApi, cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverIntakeConfigurationPolicy intakeConfiguration) {
        return new CutoverTaskApplicationService(taskMapper, deviceMapper, historyMapper, assessmentMapper,
                configurationMapper, projectScopePort, projectContextPort, deviceScopePort, deviceProductTypePort,
                customerLevelPort, readinessPort, commandExecutionApi, Clock.systemDefaultZone(), intakeConfiguration);
    }

    @Bean
    CutoverTaskQueryService cutoverTaskQueryService(CutoverTaskMapper taskMapper, CutoverTaskDeviceScopeMapper deviceMapper,
            CutoverAssessmentMapper assessmentMapper,
            CutoverChecklistMapper checklistMapper, CutoverConfigurationRevisionMapper configurationMapper,
            CutoverProjectScopePort projectScopePort, CutoverProjectContextPort projectContextPort,
            CutoverDeviceScopePort deviceScopePort, CutoverCustomerLevelPort customerLevelPort,
            CutoverReadinessPort readinessPort, CutoverFrozenDeviceMapper frozenDevices) {
        return new CutoverTaskQueryService(taskMapper, deviceMapper, assessmentMapper, checklistMapper,
                configurationMapper, projectScopePort, projectContextPort, deviceScopePort, customerLevelPort,
                readinessPort, Clock.systemDefaultZone(), frozenDevices);
    }

    @Bean
    CutoverTaskRequestCodec cutoverTaskRequestCodec(ObjectMapper mapper) {
        return new CutoverTaskRequestCodec(mapper);
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
