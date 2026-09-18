package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxAppended;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleOutboxDeliveryJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectOperationResultDeliveryTest {
    static BusinessOperationResultEvent source() {
        return new BusinessOperationResultEvent(UUID.randomUUID().toString(),1,1L,9L,"SOL","SITE_SURVEY","11",null,
                3,"SOL:SITE_SURVEY:11:3:1","SURVEY_CONFIRMED","SOL.SITE_SURVEY.CONFIRM","command-1",7L,LocalDateTime.of(2026,9,17,12,30,0,123000000),"trace-1");
    }
    @Test void stableRecipientIdentitySeparatesRoundsAndTargets() {
        var event = source(); String a = ProjectResultTargetEvent.id(event.eventId(),"TASK",10L,20L,30L,40L);
        assertEquals(a,ProjectResultTargetEvent.id(event.eventId(),"TASK",10L,20L,30L,40L));
        assertNotEquals(a,ProjectResultTargetEvent.id(event.eventId(),"TASK",10L,20L,31L,40L));
        assertNotEquals(a,ProjectResultTargetEvent.id(event.eventId(),"STAGE",10L,20L,30L,40L));
        assertThrows(IllegalArgumentException.class,() -> new ProjectResultTargetEvent("forged",event,"TASK",10L,"task:X",20L,30L,40L));
    }
    @Test void domainEventHasNoRequiredProjectExecutionIdentity() {
        var json = JsonUtils.parseTree(JsonUtils.toJsonString(source()));
        for (String forbidden : java.util.List.of("taskId","stageId","executionId","planVersionId","templateId")) assertFalse(json.has(forbidden));
    }
    @Test void tenantAndEventEnvelopeAreVerifiedBeforeFanout() {
        var fanout=mock(ProjectOperationResultFanout.class);var processor=mock(ProjectOperationNodeResultProcessor.class);
        var delivery=new ProjectOperationResultDelivery(fanout,processor);var source=source();
        try(var tenant=mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(2L);
            assertThrows(IllegalArgumentException.class,() -> delivery.deliver(new PlatformOutboxMessageDTO(source.eventId(),
                    BusinessOperationResultEvent.EVENT_TYPE,JsonUtils.toJsonString(source),0,1L,source.occurredAt())));
            verifyNoInteractions(fanout,processor);
        }
    }
    @Test void waitingNodeIsAnAcknowledgedEvaluationNotABusinessRollback() {
        var fanout=mock(ProjectOperationResultFanout.class);var processor=mock(ProjectOperationNodeResultProcessor.class);
        var source=source();String id=ProjectResultTargetEvent.id(source.eventId(),"TASK",10L,20L,30L,40L);
        var target=new ProjectResultTargetEvent(id,source,"TASK",10L,"task:X",20L,30L,40L);
        when(processor.process(target)).thenReturn("REEVALUATED_WAITING_OR_TERMINAL");
        try(var tenant=mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(1L);
            assertTrue(new ProjectOperationResultDelivery(fanout,processor).deliver(new PlatformOutboxMessageDTO(id,
                    ProjectResultTargetEvent.EVENT_TYPE,JsonUtils.toJsonString(target),0,1L,source.occurredAt())));
            verify(processor).process(target);verifyNoInteractions(fanout);
        }
    }
    @Test @SuppressWarnings("unchecked") void rejectedImmediateDispatchDoesNotFailTheCommittedProducer() {
        ObjectProvider<ProjectRuleOutboxDeliveryJob> jobs=mock(ObjectProvider.class);
        var listener=new ProjectOperationCommittedResultListener(jobs,task -> {throw new java.util.concurrent.RejectedExecutionException();});
        var event=source();
        assertDoesNotThrow(() -> listener.committed(new PlatformOutboxAppended(new PlatformOutboxMessageDTO(event.eventId(),
                BusinessOperationResultEvent.EVENT_TYPE,JsonUtils.toJsonString(event),0,1L,event.occurredAt()),null)));
        verifyNoInteractions(jobs);
    }
    @Test void invalidFactVersionOrSchemaCannotBecomeAResult() {
        var event=source();
        assertThrows(IllegalArgumentException.class,() -> new BusinessOperationResultEvent(event.eventId(),2,1L,9L,"SOL","SITE_SURVEY","11",null,
                3,"fact","CONFIRMED","CONFIRM","key",7L,event.occurredAt(),"trace"));
        assertThrows(IllegalArgumentException.class,() -> event.requireEnvelope(UUID.randomUUID().toString(),1L));
    }
}
