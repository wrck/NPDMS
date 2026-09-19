package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultRecordingApi;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import cn.iocoder.yudao.module.pms.acceptance.service.operation.AcceptanceOperationResultBridge;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectResultRecordingProducersTest {
    private final ProjectBusinessResultRecordingApi recorder=mock(ProjectBusinessResultRecordingApi.class);
    private final PlatformBusinessEventApi outbox=mock(PlatformBusinessEventApi.class);
    private final AcceptanceActivityMapper activities=mock(AcceptanceActivityMapper.class);
    private final ProjectOperationResultOutbox controlled=new ProjectOperationResultOutbox(of(recorder),outbox);
    private final AcceptanceOperationResultBridge nativeBridge=new AcceptanceOperationResultBridge(of(recorder),of(activities),of(outbox));
    @BeforeEach void before(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void after(){TenantContextHolder.clear();}

    @Test void controlledResultRecordsExactlyTheLegacyEventAfterTheOwnerCommand() {
        controlled.append("CONFIRM",1,new ProjectOperationCommand(3L,"TASK",4L,null,"100",1,"fact",JsonUtils.parseTree("{}"),"key"),
                new ProjectOperationResult("ACC","ACCEPTANCE","100","40",2,"fact","REPORT_VERSION_PUBLISHED",null,false),1L,9L,"trace");
        var event=ArgumentCaptor.forClass(BusinessOperationResultEvent.class);var payload=ArgumentCaptor.forClass(BusinessEvent.class);
        var order=inOrder(recorder,outbox);order.verify(recorder).record(event.capture());order.verify(outbox).append(eq("ACCEPTANCE"),eq("100"),payload.capture());
        assertEquals(event.getValue().eventId(),payload.getValue().eventId());
        assertEquals(JsonUtils.parseTree(JsonUtils.toJsonString(event.getValue())),JsonUtils.parseTree(payload.getValue().eventPayload()));
        assertEquals("key",event.getValue().commandId());assertEquals("trace",event.getValue().correlationId());
    }
    @Test void journalFailureDoesNotProduceALegacySuccessEvent() {
        doThrow(new IllegalStateException("journal unavailable")).when(recorder).record(any());
        assertThrows(IllegalStateException.class,()->controlled.append("CONFIRM",1,
                new ProjectOperationCommand(3L,"TASK",4L,null,"100",1,"fact",JsonUtils.parseTree("{}"),"key"),
                new ProjectOperationResult("ACC","ACCEPTANCE","100","40",2,"fact","REPORT_VERSION_PUBLISHED",null,false),1L,9L,"trace"));
        verifyNoInteractions(outbox);
    }
    @ParameterizedTest @ValueSource(strings={"PUBLISHED","REVOKED"})
    void ordinaryReportEventsKeepTheNativeVersionAndDoNotCompleteAcceptance(String action) {
        var activity=new AcceptanceActivityDO();activity.setId(100L);activity.setTenantId(1L);activity.setProjectId(3L);activity.setVersion(2);activity.setActivityStatus("PENDING");
        when(activities.selectById(100L)).thenReturn(activity);
        var appended=appended(action);nativeBridge.onAppended(appended);
        var event=ArgumentCaptor.forClass(BusinessOperationResultEvent.class);verify(recorder).record(event.capture());
        assertEquals("40",event.getValue().revisionId());assertEquals("100",event.getValue().objectId());
        assertEquals(appended.message().eventId(),event.getValue().commandId());assertEquals("PENDING",activity.getActivityStatus());
        verify(outbox).append(eq("ACCEPTANCE"),eq("100"),any());
    }
    @Test void controlledReportDoesNotAlsoRecordTheNativeBridgeEvent() {
        var selection=new ProjectBusinessExecutionSelection(new ProjectTaskExecutionContext(3L,1,4L,1,5L,1,6L,7L,1,1,8L,1,true,null),null);
        try(var scope=ProjectVerifiedOperationScope.open(new ProjectVerifiedOperationScope.Frame(1L,9L,3L,"ACC","ACCEPTANCE","ACC.ACCEPTANCE_REPORT.PUBLISH",1,"100",selection))){
            nativeBridge.onAppended(appended("PUBLISHED"));verifyNoInteractions(recorder,outbox,activities);
        }
    }
    private PlatformOutboxAppended appended(String action) {
        String id=UUID.randomUUID().toString();boolean revoked=action.equals("REVOKED");
        var source=new AcceptanceReportVersionChangedMessage(id,1L,action,100L,3L,50L,"PRELIMINARY",9L,revoked?null:40L,revoked?40L:null,1,List.of());
        return new PlatformOutboxAppended(new PlatformOutboxMessageDTO(id,"AcceptanceReportVersionChanged",JsonUtils.toJsonString(source),0,1L,LocalDateTime.now()),null);
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value){ObjectProvider<T> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(value);return provider;}
}
