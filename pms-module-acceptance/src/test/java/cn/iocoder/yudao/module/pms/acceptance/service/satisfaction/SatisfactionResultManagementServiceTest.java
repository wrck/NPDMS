package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionCollectionTaskDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.SatisfactionResultDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SatisfactionResultManagementServiceTest {
    private final SatisfactionResultMapper results=mock(SatisfactionResultMapper.class);
    private final SatisfactionResultFileMapper files=mock(SatisfactionResultFileMapper.class);
    private final SatisfactionCollectionTaskMapper tasks=mock(SatisfactionCollectionTaskMapper.class);
    private final ProjectScopeApi scope=mock(ProjectScopeApi.class);
    private final ProjectWorkBindingFactApi bindings=mock(ProjectWorkBindingFactApi.class);
    private final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    private final SatisfactionResultManagementService service=new SatisfactionResultManagementService(
            results,files,tasks,scope,bindings,mock(FileArtifactApi.class),commands);

    @Test void invalidationKeepsFrozenDeliverableAfterTaskRename() {
        var task=fixture();
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L,3L,Set.of(20L),Set.of()));
        when(bindings.lockCurrentSatisfactionTask(any())).thenReturn(new ProjectSatisfactionTaskFact(
                20L,21L,"RENAMED-SAT",9,"AFTER_INITIAL_ACCEPTANCE",30L,31L,1,"RULE-1",BigDecimal.ONE,99L));
        when(results.invalidateCurrent(any())).thenReturn(1);
        when(commands.execute(any(),any(),any(),any(),any())).thenAnswer(invocation -> {
            Supplier<?> operation=invocation.getArgument(3);
            Function<Object,PlatformCommandExecutionApi.SuccessFacts> facts=invocation.getArgument(4);
            var result=operation.get();
            var event=facts.apply(result).businessEvents().getFirst();
            assertTrue(event.eventPayload().contains("\"deliverableId\":40"));
            assertTrue(event.eventPayload().contains("\"changeType\":\"INVALIDATED\""));
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,result);
        });
        var result=service.invalidate(7L,99L,12L,0,"OWNER_INVALIDATED","reason","operation-1");
        assertEquals(40L,result.deliverableId()); assertEquals(9,result.projectTaskVersion());
        assertEquals(1,result.resultFactVersion()); assertEquals(40L,task.getDeliverableId());
    }

    @Test void missingFrozenBindingDoesNotInvalidateOwnerResult() {
        fixture().setDeliverableId(null);
        assertThrows(IllegalStateException.class,
                () -> service.invalidateOnce(7L,99L,12L,0,"OWNER_INVALIDATED","reason","operation-2"));
        verify(results,never()).invalidateCurrent(any());
        verifyNoInteractions(scope,bindings,files);
    }

    private SatisfactionCollectionTaskDO fixture() {
        var task=new SatisfactionCollectionTaskDO();
        task.setId(10L); task.setTenantId(7L); task.setProjectId(20L); task.setProjectTaskId(21L);
        task.setDeliverableId(40L); task.setResultId(12L); task.setCollectionKey("SAT-10"); task.setTaskRevisionNo(1);
        var result=new SatisfactionResultDO();
        result.setId(12L); result.setTenantId(7L); result.setCollectionTaskId(10L); result.setVersion(0);
        result.setResultVersion(1); result.setResultStatus("EFFECTIVE"); result.setPassed(true); result.setArchiveActorUserId(99L);
        when(results.selectById(12L)).thenReturn(result); when(results.selectByIdForUpdate(7L,12L)).thenReturn(result);
        when(tasks.selectByIdForUpdate(7L,10L)).thenReturn(task);
        return task;
    }
}
