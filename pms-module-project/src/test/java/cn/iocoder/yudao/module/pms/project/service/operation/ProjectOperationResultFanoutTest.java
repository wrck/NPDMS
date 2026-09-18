package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectOperationResultFanoutTest {
    @Test @SuppressWarnings("unchecked") void receptionCreatesTwoDurableTargetsWithoutRunningNodeCommands() {
        var projects=mock(ProjectMasterMapper.class);var plans=mock(ProjectPlanVersionMapper.class);var nodes=mock(ProjectNodeExecutionMapper.class);
        var commands=mock(PlatformCommandExecutionApi.class);var outbox=mock(PlatformBusinessEventApi.class);
        var project=new ProjectMasterDO();project.setId(9L);project.setTenantId(1L);project.setActivePlanVersionId(20L);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        var plan=new ProjectPlanVersionDO();plan.setId(20L);plan.setProjectId(9L);plan.setTenantId(1L);
        plan.setExecutionSnapshot("{\"executionSchemaVersion\":2,\"tasks\":[{\"nodeKey\":\"a\",\"binding\":{\"targetContextCode\":\"SOL\",\"targetObjectType\":\"SITE_SURVEY\",\"operationContract\":{}}},{\"nodeKey\":\"b\",\"binding\":{\"targetContextCode\":\"SOL\",\"targetObjectType\":\"SITE_SURVEY\",\"operationContract\":{}}}]}");
        when(plans.selectEffective(any())).thenReturn(plan);when(nodes.selectCurrent(any())).thenReturn(List.of(round(30L,10L,"a"),round(31L,11L,"b")));
        when(commands.execute(any(),anyString(),eq(ProjectOperationResultFanout.Receipt.class),any(),any())).thenAnswer(invocation ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier<ProjectOperationResultFanout.Receipt>)invocation.getArgument(3)).get()));
        try(var tenant=mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(1L);
            var fanout=new ProjectOperationResultFanout(projects,plans,nodes,commands,outbox);
            var source=ProjectOperationResultDeliveryTest.source();var result=fanout.accept(source);
            assertEquals(2,result.recipients().size());assertNotEquals(result.recipients().get(0),result.recipients().get(1));
            verify(outbox,times(2)).append(eq("ProjectResultTarget"),anyString(),any());
            reset(outbox);
            when(commands.execute(any(),anyString(),eq(ProjectOperationResultFanout.Receipt.class),any(),any())).thenReturn(
                    new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED,result));
            assertEquals(result,fanout.accept(source));verifyNoInteractions(outbox);
        }
    }
    @Test @SuppressWarnings("unchecked") void noSubscriptionIsAReceiptNotAnInfiniteDeliveryFailure() {
        var projects=mock(ProjectMasterMapper.class);var plans=mock(ProjectPlanVersionMapper.class);var nodes=mock(ProjectNodeExecutionMapper.class);
        var commands=mock(PlatformCommandExecutionApi.class);var outbox=mock(PlatformBusinessEventApi.class);
        var project=new ProjectMasterDO();project.setId(9L);project.setTenantId(1L);when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        when(commands.execute(any(),anyString(),eq(ProjectOperationResultFanout.Receipt.class),any(),any())).thenAnswer(invocation ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier<ProjectOperationResultFanout.Receipt>)invocation.getArgument(3)).get()));
        try(var tenant=mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(1L);
            assertTrue(new ProjectOperationResultFanout(projects,plans,nodes,commands,outbox).accept(ProjectOperationResultDeliveryTest.source()).recipients().isEmpty());
            verifyNoInteractions(outbox,nodes);
        }
    }
    @Test void changedPayloadForSameEventDoesNotOverwriteReceipt() {
        var commands=mock(PlatformCommandExecutionApi.class);
        when(commands.execute(any(),anyString(),eq(ProjectOperationResultFanout.Receipt.class),any(),any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.CONFLICT,null));
        var outbox=mock(PlatformBusinessEventApi.class);
        try(var tenant=mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(1L);
            var fanout=new ProjectOperationResultFanout(mock(ProjectMasterMapper.class),mock(ProjectPlanVersionMapper.class),mock(ProjectNodeExecutionMapper.class),commands,outbox);
            assertThrows(IllegalStateException.class,() -> fanout.accept(ProjectOperationResultDeliveryTest.source()));verifyNoInteractions(outbox);
        }
    }
    private static ProjectNodeExecutionDO round(long id,long node,String key) {
        var row=new ProjectNodeExecutionDO();row.setId(id);row.setTenantId(1L);row.setProjectId(9L);row.setPlanVersionId(20L);
        row.setNodeKind("TASK");row.setNodeInstanceId(node);row.setNodeKey(key);row.setContractId(40L+id);return row;
    }
}
