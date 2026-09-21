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
        assertTrue(validator.validate(subscription("PINNED_RESULT","HISTORICAL_FACT"),"test").stream()
                .anyMatch(issue -> issue.code().equals("RESULT_COMMIT_BARRIER_UNAVAILABLE")));
        assertTrue(validator.validate(subscription("NEW_RESULT","CURRENT_VALID"),"test").stream()
                .anyMatch(issue -> issue.code().equals("RESULT_COMMIT_BARRIER_UNAVAILABLE")));
        verify(source,never()).inspect(any());
    }
    @Test void currentOnlySourceDoesNotAcquireHistoricalCapabilitiesByConfiguration() {
        var issues = validator(false,false).validate(subscription("PINNED_RESULT","HISTORICAL_FACT"),"test");
        assertEquals(Set.of("RESULT_EXACT_LOOKUP_UNSUPPORTED","RESULT_HISTORY_UNSUPPORTED", "RESULT_CHANGE_SOURCE_UNAVAILABLE", "RESULT_COMMIT_BARRIER_UNAVAILABLE"),
                issues.stream().map(issue->issue.code()).collect(java.util.stream.Collectors.toSet()));
        assertFalse(validator(false,false).validate(subscription("REUSE_EXISTING","CURRENT_VALID"),"test").isEmpty());
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
        assertEquals(Set.of("RESULT_CHANGE_SOURCE_UNAVAILABLE", "RESULT_INVENTORY_UNAVAILABLE", "RESULT_COMMIT_BARRIER_UNAVAILABLE"),
                issues.stream().map(issue->issue.code()).collect(java.util.stream.Collectors.toSet()));
        verifyNoInteractions(registry); verify(source,never()).inspect(any());
    }
    @Test void transactionalInventoryAndChangeSourceEnablesNewResultsWithoutCallingOwner() {
        var provider = mock(cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChangeSource.class,
                withSettings().extraInterfaces(cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.class));
        when(provider.descriptor()).thenReturn(new Descriptor(type, true, true, true));
        when(provider.transactionalChangeCoverage()).thenReturn(true);
        var validator = new TemplateResultSubscriptionCapabilities(new ProjectBusinessResultSources(List.of(provider)));
        for (var acquisition : List.of("REUSE_EXISTING", "NEW_RESULT", "PINNED_RESULT"))
            assertTrue(validator.validate(subscription(acquisition, "HISTORICAL_FACT"), "test").isEmpty());
        verify(provider, never()).inspect(any());
        when(provider.transactionalChangeCoverage()).thenReturn(false);
        assertTrue(validator.validate(subscription("NEW_RESULT", "CURRENT_VALID"), "test").stream()
                .anyMatch(issue -> issue.code().equals("RESULT_COMMIT_BARRIER_UNAVAILABLE")));
    }
    private Subscription subscription(String acquisition,String validity) {
        return new Subscription("s",type.ownerContext(),type.entityType(),type.resultType(),new Scope("OBJECTS",List.of("100")),
                new Policy(acquisition,validity,"EXACT_ONE",acquisition.equals("PINNED_RESULT")?"40":null));
    }
}
