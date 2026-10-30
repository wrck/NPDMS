package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectTaskAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectStageCompletionService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectOperationNodeResultProcessorTest {
    @Test void lateOldPlanRecipientNeverCompletesTheNewRound() {
        var projects=mock(ProjectMasterMapper.class);var plans=mock(ProjectPlanVersionMapper.class);
        var nodes=mock(ProjectNodeExecutionMapper.class);var taskAdmission=mock(ProjectTaskAdmissionService.class);
        var stageAdmission=mock(ProjectStageAdmissionService.class);var associations=mock(ProjectTaskBusinessAssociationService.class);
        var tasks=mock(ProjectTaskLifecycleService.class);var stages=mock(ProjectStageCompletionService.class);
        var outbox=mock(PlatformBusinessEventApi.class);var audit=mock(OperationAuditApi.class);
        var project=new ProjectMasterDO();project.setId(9L);project.setTenantId(1L);project.setLifecycleStatus("ACTIVE");project.setActivePlanVersionId(21L);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        var plan=new ProjectPlanVersionDO();plan.setId(21L);when(plans.selectEffective(any())).thenReturn(plan);
        var source=ProjectOperationResultDeliveryTest.source();var target=new ProjectResultTargetEvent(
                ProjectResultTargetEvent.id(source.eventId(),"TASK",10L,20L,30L,40L),source,"TASK",10L,"a",20L,30L,40L);
        try(var tenant=mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(1L);
            assertEquals("OBSOLETE_RECIPIENT",new ProjectOperationNodeResultProcessor(projects,plans,nodes,taskAdmission,stageAdmission,
                    associations,tasks,stages,outbox,audit).process(target));
            verifyNoInteractions(tasks,stages,associations,outbox,nodes);
        }
    }
}
