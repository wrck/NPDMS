package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicFormBusinessInstanceServiceTest {

    @Mock DynamicFormBusinessObjectPolicyProviderRegistry policyRegistry;
    @Mock DynamicFormTemplateMapper templateMapper;
    @Mock DynamicFormTemplateRevisionMapper revisionMapper;
    @Mock PlatformDynamicFormInstanceMapper instanceMapper;
    @Mock FileArtifactApi fileArtifactApi;

    private DynamicFormBusinessInstanceService service;

    @BeforeEach
    void setUp() {
        service = new DynamicFormBusinessInstanceService(policyRegistry, templateMapper, revisionMapper,
                instanceMapper, new DynamicFormSchemaService(), fileArtifactApi);
    }

    @Test
    void businessActionValueSetIsClosed() {
        assertThat(DynamicFormBusinessAction.values()).containsExactly(
                DynamicFormBusinessAction.REVISION_BINDING_PUBLISH,
                DynamicFormBusinessAction.REVISION_FROZEN_USE,
                DynamicFormBusinessAction.CREATE,
                DynamicFormBusinessAction.READ,
                DynamicFormBusinessAction.PATCH,
                DynamicFormBusinessAction.COMPLETE,
                DynamicFormBusinessAction.CLONE_SOURCE,
                DynamicFormBusinessAction.CLONE_TARGET,
                DynamicFormBusinessAction.FILE_READ,
                DynamicFormBusinessAction.FILE_WRITE);
    }

    @Test
    void everyWriteAndLockApiRequiresCallerTransaction() throws Exception {
        for (String method : new String[]{"lockAndRevalidateRevisionForUsage", "createBusinessInstance",
                "patchInstanceValues", "cloneBusinessInstance", "lockAndRevalidateInstance"}) {
            java.lang.reflect.Method apiMethod = java.util.Arrays.stream(
                    DynamicFormBusinessInstanceApi.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow();
            Transactional transactional = DynamicFormBusinessInstanceApiImpl.class
                    .getMethod(method, apiMethod.getParameterTypes()).getAnnotation(Transactional.class);
            assertThat(transactional).as(method).isNotNull();
            assertThat(transactional.propagation()).as(method).isEqualTo(Propagation.MANDATORY);
        }
    }

    @Test
    void createRejectsActionSubstitution() {
        var command = new DynamicFormInstanceCreateCommand(1L, 2L,
                new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS"),
                DynamicFormBusinessAction.PATCH, 100L,
                new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", "10"),
                20L, 1, Map.of());
        assertThatThrownBy(() -> service.createBusinessInstance(command))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inspectsExactEnabledPublishedRevisionAndFreezesProviderAction() {
        DynamicFormTemplateRevisionDO revision = revision();
        DynamicFormTemplateDO template = template();
        when(revisionMapper.selectByRow(any())).thenReturn(revision);
        when(templateMapper.selectByRow(any())).thenReturn(template);
        DynamicFormPolicyFact policy = policy(DynamicFormBusinessAction.REVISION_BINDING_PUBLISH, 9L, "COMPATIBLE");
        when(policyRegistry.inspectRevision(any())).thenReturn(policy);

        DynamicFormRevisionFact fact = service.inspectRevisionForUsage(new DynamicFormRevisionUsageQuery(
                1L, 2L, providerKey(), 20L, "PRE_04_REQUIREMENT_ANALYSIS",
                DynamicFormBusinessAction.REVISION_BINDING_PUBLISH, 3));

        assertThat(fact.templateId()).isEqualTo(10L);
        assertThat(fact.templateRevisionId()).isEqualTo(20L);
        assertThat(fact.revisionNo()).isEqualTo(2);
        assertThat(fact.revisionFactVersion()).isEqualTo(3);
        assertThat(fact.action()).isEqualTo(DynamicFormBusinessAction.REVISION_BINDING_PUBLISH);
        assertThat(fact.fields()).extracting(DynamicFormFieldDescriptor::fieldKey)
                .containsExactly("name", "enabled", "count", "evidence");
    }

    @Test
    void createUsesPreallocatedIdAndPreservesFalseZeroAndNull() {
        DynamicFormTemplateRevisionDO revision = revision();
        when(policyRegistry.inspectInstance(any())).thenReturn(policy(DynamicFormBusinessAction.CREATE, 8L, "DRAFT"));
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy(DynamicFormBusinessAction.CREATE, 8L, "DRAFT"));
        when(revisionMapper.selectByRow(any())).thenReturn(revision);
        when(revisionMapper.selectForUpdate(any())).thenReturn(revision);
        when(instanceMapper.insert(any())).thenReturn(1);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", null);
        values.put("enabled", false);
        values.put("count", 0);

        DynamicFormInstanceFact fact = service.createBusinessInstance(new DynamicFormInstanceCreateCommand(
                1L, 2L, providerKey(), DynamicFormBusinessAction.CREATE, 100L, ownerKey(), 20L, 3, values));

        ArgumentCaptor<PlatformDynamicFormInstanceDO> inserted = ArgumentCaptor.forClass(
                PlatformDynamicFormInstanceDO.class);
        verify(instanceMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getId()).isEqualTo(100L);
        assertThat(inserted.getValue().getOwnerContext()).isEqualTo("SOL");
        assertThat(fact.instanceId()).isEqualTo(100L);
        assertThat(fact.ordinaryValues()).containsEntry("enabled", false).containsEntry("count", 0)
                .containsKey("name");
    }

    @Test
    void createReportsStableTypeAndNumericValidationBlockers() {
        DynamicFormTemplateRevisionDO revision = revision();
        revision.setFormRulesJson("[{\"type\":\"switch\",\"field\":\"enabled\"},"
                + "{\"type\":\"inputNumber\",\"field\":\"count\","
                + "\"validate\":[{\"min\":1,\"max\":10}]}]");
        when(policyRegistry.inspectInstance(any())).thenReturn(policy(DynamicFormBusinessAction.CREATE, 8L, "DRAFT"));
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy(DynamicFormBusinessAction.CREATE, 8L, "DRAFT"));
        when(revisionMapper.selectByRow(any())).thenReturn(revision);
        when(revisionMapper.selectForUpdate(any())).thenReturn(revision);
        when(instanceMapper.insert(any())).thenReturn(1);

        DynamicFormInstanceFact fact = service.createBusinessInstance(new DynamicFormInstanceCreateCommand(
                1L, 2L, providerKey(), DynamicFormBusinessAction.CREATE, 101L, ownerKey(), 20L, 3,
                Map.of("enabled", "false", "count", 11)));

        assertThat(fact.validationFact().result()).isEqualTo("INVALID");
        assertThat(fact.validationFact().blockerCodes()).containsExactly(
                "FORM_VALUE_INVALID:enabled", "FORM_VALUE_INVALID:count");
    }

    @Test
    void requiredEditorRejectsMarkupOnlyNbspAndFormatCharactersButPreservesFalseAndZero() {
        DynamicFormTemplateRevisionDO revision = revision();
        revision.setFormRulesJson("[{\"type\":\"Editor\",\"field\":\"emptyTag\",\"validate\":[{\"required\":true}]},"
                + "{\"type\":\"Editor\",\"field\":\"nbsp\",\"validate\":[{\"required\":true}]},"
                + "{\"type\":\"Editor\",\"field\":\"zeroWidth\",\"validate\":[{\"required\":true}]},"
                + "{\"type\":\"Editor\",\"field\":\"formatOnly\",\"validate\":[{\"required\":true}]},"
                + "{\"type\":\"switch\",\"field\":\"enabled\",\"validate\":[{\"required\":true}]},"
                + "{\"type\":\"inputNumber\",\"field\":\"count\",\"validate\":[{\"required\":true}]}]");
        when(policyRegistry.inspectInstance(any())).thenReturn(policy(DynamicFormBusinessAction.CREATE, 8L, "DRAFT"));
        when(policyRegistry.lockAndRevalidate(any())).thenReturn(policy(DynamicFormBusinessAction.CREATE, 8L, "DRAFT"));
        when(revisionMapper.selectByRow(any())).thenReturn(revision);
        when(revisionMapper.selectForUpdate(any())).thenReturn(revision);
        when(instanceMapper.insert(any())).thenReturn(1);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("emptyTag", "<p><br></p>");
        values.put("nbsp", "<p>&nbsp;</p>");
        values.put("zeroWidth", "\u200B");
        values.put("formatOnly", "<div>\u2060</div>");
        values.put("enabled", false);
        values.put("count", 0);

        DynamicFormInstanceFact fact = service.createBusinessInstance(new DynamicFormInstanceCreateCommand(
                1L, 2L, providerKey(), DynamicFormBusinessAction.CREATE, 102L, ownerKey(), 20L, 3, values));

        assertThat(fact.validationFact().blockerCodes()).containsExactly(
                "REQUIRED_VALUE_MISSING:emptyTag", "REQUIRED_VALUE_MISSING:nbsp",
                "REQUIRED_VALUE_MISSING:zeroWidth", "REQUIRED_VALUE_MISSING:formatOnly");
    }

    @Test
    void inspectReportsUnavailableControlledFileButKeepsEmptyAndAvailableSetsValid() {
        when(policyRegistry.inspectInstance(any())).thenReturn(
                policy(DynamicFormBusinessAction.READ, 8L, "DRAFT"));
        when(instanceMapper.selectByRow(any())).thenReturn(instance());
        when(revisionMapper.selectByRow(any())).thenReturn(revision());
        when(fileArtifactApi.inspectReferenceSets(any())).thenReturn(
                List.of(fileSet("UNAVAILABLE")), List.of(fileSet()), List.of(fileSet("AVAILABLE")));

        DynamicFormInstanceQuery query = new DynamicFormInstanceQuery(
                1L, 2L, providerKey(), ownerKey(), 100L, DynamicFormBusinessAction.READ);

        assertThat(service.inspectInstance(query).validationFact().blockerCodes())
                .containsExactly("CONTROLLED_FILE_INVALID:evidence");
        assertThat(service.inspectInstance(query).validationFact().blockerCodes()).isEmpty();
        assertThat(service.inspectInstance(query).validationFact().blockerCodes()).isEmpty();
    }

    private DynamicFormProviderKey providerKey() {
        return new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
    }

    private DynamicFormOwnerKey ownerKey() {
        return new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", "50");
    }

    private DynamicFormPolicyFact policy(DynamicFormBusinessAction action, Long version, String state) {
        return new DynamicFormPolicyFact(action, true, null, version, state);
    }

    private DynamicFormTemplateDO template() {
        DynamicFormTemplateDO row = new DynamicFormTemplateDO();
        row.setId(10L);
        row.setTenantId(1L);
        row.setAvailabilityCode("ENABLED");
        row.setCurrentPublishedRevisionId(20L);
        return row;
    }

    private DynamicFormTemplateRevisionDO revision() {
        DynamicFormTemplateRevisionDO row = new DynamicFormTemplateRevisionDO();
        row.setId(20L);
        row.setTenantId(1L);
        row.setTemplateId(10L);
        row.setRevisionNo(2);
        row.setStatusCode("PUBLISHED");
        row.setVersion(3);
        row.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        row.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        row.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        row.setFormConfJson("{}");
        row.setFormRulesJson("[{\"type\":\"input\",\"field\":\"name\"},"
                + "{\"type\":\"switch\",\"field\":\"enabled\"},"
                + "{\"type\":\"inputNumber\",\"field\":\"count\"},"
                + "{\"type\":\"PmsFileArtifact\",\"field\":\"evidence\"}]");
        return row;
    }

    private PlatformDynamicFormInstanceDO instance() {
        PlatformDynamicFormInstanceDO row = new PlatformDynamicFormInstanceDO();
        row.setId(100L);
        row.setTenantId(1L);
        row.setOwnerContext("SOL");
        row.setObjectType("REQUIREMENT_ANALYSIS");
        row.setObjectId("50");
        row.setTemplateId(10L);
        row.setTemplateRevisionId(20L);
        row.setTemplateRevisionNo(2);
        row.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        row.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        row.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        row.setValueJson("{}");
        row.setVersion(1);
        return row;
    }

    private FileReferenceSetFact fileSet(String... availabilityStatuses) {
        List<FileArtifactVersionFact> facts = java.util.Arrays.stream(availabilityStatuses)
                .map(status -> new FileArtifactVersionFact(200L, 1, "evidence-slot", "DYNAMIC_FORM_ATTACHMENT",
                        "evidence.pdf", 3L, "application/pdf", null, status, "ACTIVE",
                        new FileFactVersion(1, 1, 1), 8L))
                .toList();
        return new FileReferenceSetFact(new FileReferenceSetKey("PLATFORM", "DYNAMIC_FORM_INSTANCE", "100",
                DynamicFormSchemaService.FILE_PURPOSE_PREFIX + "evidence"), 8L, facts);
    }
}
