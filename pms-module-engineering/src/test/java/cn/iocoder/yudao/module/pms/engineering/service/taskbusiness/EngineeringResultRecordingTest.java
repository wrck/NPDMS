package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultRecordingApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineeringResultRecordingTest {
    private final ProjectBusinessResultRecordingApi recorder=mock(ProjectBusinessResultRecordingApi.class);
    private final PlatformBusinessEventApi outbox=mock(PlatformBusinessEventApi.class);
    private final OwnerOperationResultSource source=mock(OwnerOperationResultSource.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<OwnerOperationResultSource> sources=mock(ObjectProvider.class);
    private final EngineeringRuleReevaluationEvents events=new EngineeringRuleReevaluationEvents(of(recorder),outbox,sources);
    @BeforeEach void before(){TenantContextHolder.setTenantId(1L);when(sources.orderedStream()).thenAnswer(call->Stream.of(source));when(source.supports(anyString())).thenReturn(true);}
    @AfterEach void after(){TenantContextHolder.clear();}

    @ParameterizedTest @ValueSource(strings={"SiteSurvey","RequirementAnalysis"})
    void ordinaryOwnerMutationUsesTheOriginalEventAndTrace(String aggregate){
        String type=aggregate.equals("SiteSurvey")?"SITE_SURVEY":"REQUIREMENT_ANALYSIS";
        when(source.current(1L,3L,aggregate,40L)).thenReturn(new ProjectOperationResult("SOL",type,"40",type.equals("SITE_SURVEY")?null:"40",2,"native-fact","COMPLETED",null,false));
        events.changed(3L,aggregate,40L,9L,"trace");
        var recorded=ArgumentCaptor.forClass(BusinessOperationResultEvent.class);verify(recorder).record(recorded.capture());
        var payload=ArgumentCaptor.forClass(BusinessEvent.class);verify(outbox).append(eq(type),eq("40"),payload.capture());
        assertEquals(recorded.getValue().eventId(),payload.getValue().eventId());assertEquals("trace",recorded.getValue().correlationId());
        assertEquals(JsonUtils.parseTree(JsonUtils.toJsonString(recorded.getValue())),JsonUtils.parseTree(payload.getValue().eventPayload()));
    }
    @Test void missingNativeResultDoesNotCreateAFakeJournalSuccess(){
        events.changed(3L,"SiteSurvey",40L,9L,"trace");verifyNoInteractions(recorder);verify(outbox,times(1)).append(anyString(),anyString(),any());
    }
    @Test void exactControlledScopeLeavesResultRecordingToThePostCheckedExecutor(){
        var selection=new ProjectBusinessExecutionSelection(new ProjectTaskExecutionContext(3L,1,4L,1,5L,1,6L,7L,1,1,8L,1,true,null),null);
        try(var scope=ProjectVerifiedOperationScope.open(new ProjectVerifiedOperationScope.Frame(1L,9L,3L,"SOL","SITE_SURVEY","SOL.SITE_SURVEY.CONFIRM",1,"40",selection))){
            events.changed(3L,"SiteSurvey",40L,9L,"trace");verifyNoInteractions(recorder,source);verify(outbox,times(1)).append(anyString(),anyString(),any());
        }
    }
    @Test void formationHookDoesNotPublishADuplicateLegacyBusinessResult() {
        when(source.current(1L,3L,"RequirementAnalysis",40L)).thenReturn(new ProjectOperationResult("SOL","REQUIREMENT_ANALYSIS","40","40",2,"frozen","REQUIREMENT_ANALYSIS_COMPLETED",null,false));
        events.formed(3L,"RequirementAnalysis",40L,9L,"trace");
        var event=ArgumentCaptor.forClass(BusinessOperationResultEvent.class);verify(recorder).record(event.capture());
        assertEquals("OWNER.RequirementAnalysis.FORMED",event.getValue().operationCode());
        assertEquals("40",event.getValue().revisionId());verifyNoInteractions(outbox);
    }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> of(T value){ObjectProvider<T> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(value);return provider;}
}
