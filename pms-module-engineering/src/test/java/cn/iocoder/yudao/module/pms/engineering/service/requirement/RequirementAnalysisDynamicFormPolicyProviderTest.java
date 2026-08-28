package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormInstancePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormOwnerKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionPolicyQuery;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RequirementAnalysisDynamicFormPolicyProviderTest {

    private static final long TENANT = 1L;
    private static final long ACTOR = 21L;
    private static final long PROJECT = 31L;
    private static final long ROOT = 41L;

    private static final Set<String> REQUIRED = Set.of(
            "PROJECT_BACKGROUND", "PROJECT_OBJECTIVE", "NETWORK_TOPOLOGY");
    private static final List<String> CORE = List.of(
            "PROJECT_BACKGROUND", "PROJECT_OBJECTIVE", "NETWORK_TOPOLOGY",
            "TRANSMISSION_REQUIREMENT", "TRAFFIC_REQUIREMENT", "BUSINESS_REQUIREMENT",
            "IP_PLANNING", "REDUNDANCY_REQUIREMENT", "SECURITY_PROTECTION",
            "OPERATIONS_REQUIREMENT", "LOGGING_REQUIREMENT");

    @Test
    void revisionCompatibilityRejectsARequiredAttachmentSlot() {
        RequirementAnalysisDynamicFormPolicyProvider provider = new RequirementAnalysisDynamicFormPolicyProvider(
                mock(RequirementAnalysisRootMapper.class), mock(ProjectScopeApi.class),
                mock(ProjectParticipantFactApi.class), mock(PermissionApi.class));
        List<DynamicFormFieldDescriptor> fields = compatibleFields();
        int attachment = 1;
        DynamicFormFieldDescriptor original = fields.get(attachment);
        fields.set(attachment, new DynamicFormFieldDescriptor(original.fieldKey(), original.componentType(),
                true, true, original.valueType(), original.minLength(), original.maxLength(), original.pattern(),
                original.allowedValues()));

        var fact = provider.inspectRevisionCompatibility(new DynamicFormRevisionPolicyQuery(
                0L, 9L, new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS"),
                10L, 11L, 1, 1, RequirementAnalysisDynamicFormPolicyProvider.REQUIRED_USAGE,
                DynamicFormBusinessAction.REVISION_BINDING_PUBLISH, fields));

        assertFalse(fact.allowed());
    }

    @Test
    void fileReadRejectsManageOnlyActorDuringInspectAndLockedRevalidation() {
        RequirementAnalysisRootMapper rootMapper = mock(RequirementAnalysisRootMapper.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        RequirementAnalysisDynamicFormPolicyProvider provider = new RequirementAnalysisDynamicFormPolicyProvider(
                rootMapper, scopeApi, mock(ProjectParticipantFactApi.class), permissionApi);
        PreparationDO root = root();
        when(rootMapper.selectById(any())).thenReturn(root);
        when(rootMapper.selectForUpdate(any())).thenReturn(root);
        when(scopeApi.resolveCurrent(any())).thenReturn(scope());
        when(scopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(permissionApi.hasAnyPermissions(ACTOR, "pms:requirement-analysis:query",
                "pms:requirement-analysis:manage")).thenReturn(true);

        DynamicFormPolicyFact inspected = provider.inspectInstanceOwnerPolicy(query(DynamicFormBusinessAction.FILE_READ));
        assertFalse(inspected.allowed());

        DynamicFormPolicyFact staleAllowed = new DynamicFormPolicyFact(DynamicFormBusinessAction.FILE_READ,
                true, null, 7L, ROOT + ":COMPLETED:3");
        DynamicFormPolicyFact locked = provider.lockAndRevalidateInstanceOwnerPolicy(
                new DynamicFormPolicyRevalidationQuery(TENANT, ACTOR, provider.providerKey(), owner(),
                        51L, staleAllowed));
        assertFalse(locked.allowed());
        verify(permissionApi, atLeastOnce()).hasAnyPermissions(ACTOR, "pms:requirement-analysis:query");
    }

    @Test
    void fileReadAllowsQueryActorWithProjectViewDuringInspectAndLockedRevalidation() {
        RequirementAnalysisRootMapper rootMapper = mock(RequirementAnalysisRootMapper.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        RequirementAnalysisDynamicFormPolicyProvider provider = new RequirementAnalysisDynamicFormPolicyProvider(
                rootMapper, scopeApi, mock(ProjectParticipantFactApi.class), permissionApi);
        PreparationDO root = root();
        when(rootMapper.selectById(any())).thenReturn(root);
        when(rootMapper.selectForUpdate(any())).thenReturn(root);
        when(scopeApi.resolveCurrent(any())).thenReturn(scope());
        when(scopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(permissionApi.hasAnyPermissions(ACTOR, "pms:requirement-analysis:query")).thenReturn(true);

        DynamicFormPolicyFact inspected = provider.inspectInstanceOwnerPolicy(query(DynamicFormBusinessAction.FILE_READ));
        assertTrue(inspected.allowed());
        DynamicFormPolicyFact locked = provider.lockAndRevalidateInstanceOwnerPolicy(
                new DynamicFormPolicyRevalidationQuery(TENANT, ACTOR, provider.providerKey(), owner(),
                        51L, inspected));
        assertTrue(locked.allowed());
    }

    private DynamicFormInstancePolicyQuery query(DynamicFormBusinessAction action) {
        return new DynamicFormInstancePolicyQuery(TENANT, ACTOR,
                new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS"), owner(), 51L, action);
    }

    private DynamicFormOwnerKey owner() {
        return new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", String.valueOf(ROOT));
    }

    private PreparationDO root() {
        PreparationDO root = new PreparationDO();
        root.setId(ROOT);
        root.setTenantId(TENANT);
        root.setProjectId(PROJECT);
        root.setStatusCode("COMPLETED");
        root.setDraftMarker(0);
        root.setVersion(3);
        return root;
    }

    private ProjectScopeResult scope() {
        return new ProjectScopeResult(PROJECT, 7L, Set.of(PROJECT), Set.of());
    }

    private List<DynamicFormFieldDescriptor> compatibleFields() {
        List<DynamicFormFieldDescriptor> fields = new ArrayList<>();
        for (String code : CORE) {
            fields.add(new DynamicFormFieldDescriptor(code, "Editor", false, REQUIRED.contains(code),
                    "RICH_TEXT", null, null, null, List.of()));
            fields.add(new DynamicFormFieldDescriptor(code + "__ATTACHMENTS", "PmsFileArtifact", true, false,
                    "FILE_REFERENCE_SET", null, null, null, List.of()));
        }
        return fields;
    }
}
