package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext;
import cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext;
import cn.iocoder.yudao.module.pms.cutover.service.plan.context.CutoverPlanContext;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.context.CutoverChecklistContext;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CutoverStageRequestContexts {
    private final PermissionApi permissions;

    @Bean
    CutoverChecklistRequestContext cutoverChecklistRequestContext() {
        return () -> new CutoverChecklistContext(CutoverOwnerAdapters.tenant(),
                CutoverOwnerAdapters.actor(), UUID.randomUUID().toString());
    }

    @Bean
    CutoverPlanRequestContext cutoverPlanRequestContext() {
        return () -> {
            Long actor = CutoverOwnerAdapters.actor();
            return new CutoverPlanContext(CutoverOwnerAdapters.tenant(), actor,
                    UUID.randomUUID().toString(), allowed(actor, "save-plan"), allowed(actor, "save-plan"),
                    allowed(actor, "submit-plan"), allowed(actor, "download-plan"));
        };
    }

    @Bean
    CutoverApprovalRequestContext cutoverApprovalRequestContext() {
        return () -> {
            Long actor = CutoverOwnerAdapters.actor();
            return new CutoverApprovalContext(CutoverOwnerAdapters.tenant(), actor,
                    UUID.randomUUID().toString(), allowed(actor, "query-approval"), allowed(actor, "approve"),
                    allowed(actor, "reassign-approval"));
        };
    }

    @Bean
    CutoverClosureRequestContext cutoverClosureRequestContext() {
        return () -> {
            Long actor = CutoverOwnerAdapters.actor();
            return new CutoverClosureContext(CutoverOwnerAdapters.tenant(), actor,
                    UUID.randomUUID().toString(), allowed(actor, "save-closure"),
                    allowed(actor, "request-collection"), allowed(actor, "submit-closure"));
        };
    }

    private boolean allowed(Long actor, String action) {
        return permissions.hasAnyPermissions(actor, "pms:cutover-task:" + action);
    }
}
