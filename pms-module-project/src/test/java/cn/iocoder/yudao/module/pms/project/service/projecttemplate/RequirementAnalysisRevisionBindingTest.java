package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionUsageQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.RequirementAnalysisWorkBindingSchema;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class RequirementAnalysisRevisionBindingTest {

    @Mock
    private DynamicFormBusinessInstanceApi dynamicFormBusinessInstanceApi;
    private ProjectTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProjectTemplateServiceImpl();
        ReflectionTestUtils.setField(service, "dynamicFormBusinessInstanceApi", dynamicFormBusinessInstanceApi);
    }

    @Test
    void publicationUsesExactRevisionBindingPublishFactAndFreezesItsAuthoritativeIdentity() {
        TemplateDefinitionContent content = content(binding(700L, 701L, 3, 9));
        when(dynamicFormBusinessInstanceApi.inspectRevisionForUsage(any())).thenReturn(revisionFact(
                DynamicFormBusinessAction.REVISION_BINDING_PUBLISH));
        when(dynamicFormBusinessInstanceApi.lockAndRevalidateRevisionForUsage(any()))
                .thenAnswer(invocation -> invocation.<cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionRevalidationQuery>
                        getArgument(0).expectedFact());

        List<String> failures = service.freezeRequirementAnalysisBindings(content, 0L, 88L);

        assertTrue(failures.isEmpty(), () -> "PLT修订事实应通过，实际：" + failures);
        assertEquals(binding(700L, 701L, 3, 9), content.getTasks().getFirst().getBindingConfig());
        ArgumentCaptor<DynamicFormRevisionUsageQuery> captor =
                ArgumentCaptor.forClass(DynamicFormRevisionUsageQuery.class);
        verify(dynamicFormBusinessInstanceApi).inspectRevisionForUsage(captor.capture());
        assertEquals(new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS"), captor.getValue().providerKey());
        assertEquals("PRE_04_REQUIREMENT_ANALYSIS", captor.getValue().requiredUsage());
        assertEquals(DynamicFormBusinessAction.REVISION_BINDING_PUBLISH, captor.getValue().action());
        assertEquals(701L, captor.getValue().templateRevisionId());
        assertEquals(9, captor.getValue().expectedRevisionFactVersion());
    }

    @Test
    void publicationInspectsEveryRevisionBeforeTakingPlatformLocks() {
        TemplateDefinitionContent content = content(binding(700L, 702L, 4, 10));
        TemplateDefinitionContent.TaskDef second = content(binding(700L, 701L, 3, 9)).getTasks().getFirst();
        second.setTaskCode("PRE-04-B");
        content.setTasks(List.of(content.getTasks().getFirst(), second));
        DynamicFormRevisionFact revision702 = new DynamicFormRevisionFact(0L,
                new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS"), 700L, 702L, 4, 10,
                "PRE_04_REQUIREMENT_ANALYSIS", DynamicFormBusinessAction.REVISION_BINDING_PUBLISH,
                "FORM_CREATE", "3.4.0", "3.2.38", "{}", "[]", List.of(), null);
        DynamicFormRevisionFact revision701 = revisionFact(DynamicFormBusinessAction.REVISION_BINDING_PUBLISH);
        when(dynamicFormBusinessInstanceApi.inspectRevisionForUsage(any())).thenReturn(revision702, revision701);
        when(dynamicFormBusinessInstanceApi.lockAndRevalidateRevisionForUsage(any()))
                .thenAnswer(invocation -> invocation.<cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionRevalidationQuery>
                        getArgument(0).expectedFact());

        assertTrue(service.freezeRequirementAnalysisBindings(content, 0L, 88L).isEmpty());

        InOrder order = inOrder(dynamicFormBusinessInstanceApi);
        order.verify(dynamicFormBusinessInstanceApi, org.mockito.Mockito.times(2)).inspectRevisionForUsage(any());
        order.verify(dynamicFormBusinessInstanceApi, org.mockito.Mockito.times(2))
                .lockAndRevalidateRevisionForUsage(any());
    }

    @Test
    void publicationFailsClosedWhenPlatformReturnsAChangedRevisionFact() {
        TemplateDefinitionContent content = content(binding(700L, 701L, 3, 9));
        DynamicFormRevisionFact changed = new DynamicFormRevisionFact(0L,
                new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS"), 700L, 701L, 4, 10,
                "PRE_04_REQUIREMENT_ANALYSIS", DynamicFormBusinessAction.REVISION_BINDING_PUBLISH,
                "FORM_CREATE", "3.4.0", "3.2.38", "{}", "[]", List.of(), null);
        when(dynamicFormBusinessInstanceApi.inspectRevisionForUsage(any())).thenReturn(changed);

        assertTrue(service.freezeRequirementAnalysisBindings(content, 0L, 88L).stream()
                .anyMatch(value -> value.contains("PRE-04动态表单修订无效")));
    }

    @Test
    void publicationRejectsLegacyDynamicFormRevisionColumnForPre04() {
        TemplateDefinitionContent content = content(binding(700L, 701L, 3, 9));
        content.getTasks().getFirst().setDynamicFormRevisionId(701L);

        assertTrue(service.freezeRequirementAnalysisBindings(content, 0L, 88L).stream()
                .anyMatch(value -> value.contains("PRE-04动态表单修订无效")));
        verify(dynamicFormBusinessInstanceApi, org.mockito.Mockito.never()).inspectRevisionForUsage(any());
    }

    private static TemplateDefinitionContent content(String binding) {
        TemplateDefinitionContent.TaskDef task = new TemplateDefinitionContent.TaskDef();
        task.setTaskCode("PRE-04");
        task.setWorkBindingTypeCode("BUSINESS_OBJECT");
        task.setTargetContextCode("SOL");
        task.setTargetObjectType("REQUIREMENT_ANALYSIS");
        task.setTargetObjectKey("PRE_04_REQUIREMENT_ANALYSIS");
        task.setBindingConfig(binding);
        TemplateDefinitionContent content = new TemplateDefinitionContent();
        content.setTasks(List.of(task));
        return content;
    }

    private static DynamicFormRevisionFact revisionFact(DynamicFormBusinessAction action) {
        return new DynamicFormRevisionFact(0L, new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS"),
                700L, 701L, 3, 9, "PRE_04_REQUIREMENT_ANALYSIS", action,
                "FORM_CREATE", "3.4.0", "3.2.38", "{}", "[]", List.of(), null);
    }

    private static String binding(long templateId, long revisionId, int revisionNo, int factVersion) {
        return "{\"schemaVersion\":2,\"dynamicFormTemplateId\":" + templateId
                + ",\"dynamicFormTemplateRevisionId\":" + revisionId
                + ",\"dynamicFormRevisionNo\":" + revisionNo
                + ",\"dynamicFormRevisionFactVersion\":" + factVersion + "}";
    }
}
