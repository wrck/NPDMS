package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectBusinessResultSources;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemplateResultSubscriptionCapabilitiesTest {
    private final Type type = new Type("SOL","REQUIREMENT_ANALYSIS","REQUIREMENT_ANALYSIS_COMPLETED");
    private final BusinessResultSource source = mock(BusinessResultSource.class);

    private TemplateResultSubscriptionCapabilities validator(boolean exact, boolean history) {
        when(source.descriptor()).thenReturn(new Descriptor(type,true,exact,history));
        return new TemplateResultSubscriptionCapabilities(new ProjectBusinessResultSources(List.of(source)));
    }
    @Test void retainedResultsSupportExactHistoryButNeverInventACommitBoundary() {
        var validator = validator(true,true);
        assertTrue(validator.validate(subscription("PINNED_RESULT","HISTORICAL_FACT"),"test").isEmpty());
        assertEquals("RESULT_FORMATION_BOUNDARY_UNAVAILABLE",validator.validate(subscription("NEW_RESULT","CURRENT_VALID"),"test").getFirst().code());
        verify(source,never()).inspect(any());
    }
    @Test void currentOnlySourceDoesNotAcquireHistoricalCapabilitiesByConfiguration() {
        var issues = validator(false,false).validate(subscription("PINNED_RESULT","HISTORICAL_FACT"),"test");
        assertEquals(Set.of("RESULT_EXACT_LOOKUP_UNSUPPORTED","RESULT_HISTORY_UNSUPPORTED"),
                issues.stream().map(issue->issue.code()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(validator(false,false).validate(subscription("REUSE_EXISTING","CURRENT_VALID"),"test").isEmpty());
        verify(source,never()).inspect(any());
    }
    @Test void ownerOrResultTypeCannotBeBorrowedFromANeighboringDescriptor() {
        var validator = validator(true,true);
        var original = subscription("REUSE_EXISTING","CURRENT_VALID");
        assertEquals("RESULT_SOURCE_UNAVAILABLE",validator.validate(new Subscription("s","OTHER",type.entityType(),type.resultType(),original.scope(),original.policy()),"test").getFirst().code());
        assertEquals("RESULT_SOURCE_UNAVAILABLE",validator.validate(new Subscription("s",type.ownerContext(),type.entityType(),"ACCEPTANCE_COMPLETED",original.scope(),original.policy()),"test").getFirst().code());
        verify(source,never()).inspect(any());
    }
    @Test void availableResultLookupDoesNotPrematurelyEnableSubscriptionPublication() {
        var registry = mock(ProjectBusinessOperationRegistry.class);
        var compilation = new TemplateExecutionConfigurationCompilation(registry);
        ReflectionTestUtils.setField(compilation,"resultCapabilities",validator(true,true));
        var document = new TemplateDesignerDocument();
        var stage = new TemplateDesignerDocument.StageNode(); stage.setNodeKey("stage");
        stage.setExecution(JsonUtils.parseTree("{\"subscriptions\":[{\"key\":\"s\",\"ownerContext\":\"SOL\",\"entityType\":\"REQUIREMENT_ANALYSIS\",\"resultType\":\"REQUIREMENT_ANALYSIS_COMPLETED\",\"scope\":{\"mode\":\"PROJECT\"},\"policy\":{\"acquisition\":\"REUSE_EXISTING\",\"validity\":\"CURRENT_VALID\",\"selection\":\"EXACT_ONE\"}}]}"));
        document.getStages().add(stage);
        var issues = compilation.prepare(document);
        assertEquals(List.of("RESULT_SUBSCRIPTION_NOT_INSTALLED"),issues.stream().map(issue->issue.code()).toList());
        verifyNoInteractions(registry); verify(source,never()).inspect(any());
    }
    private Subscription subscription(String acquisition,String validity) {
        return new Subscription("s",type.ownerContext(),type.entityType(),type.resultType(),new Scope("OBJECTS",List.of("100")),
                new Policy(acquisition,validity,"EXACT_ONE",acquisition.equals("PINNED_RESULT")?"40":null));
    }
}
