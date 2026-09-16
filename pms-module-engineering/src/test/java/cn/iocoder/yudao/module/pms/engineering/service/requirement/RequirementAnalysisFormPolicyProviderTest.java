package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisFormPolicyProviderTest {
    private final RequirementAnalysisAccess access = mock(RequirementAnalysisAccess.class);
    private final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
    private final RequirementAnalysisFormPolicyProvider provider = new RequirementAnalysisFormPolicyProvider(access, mapper);

    @Test void copiedSchemaPolicyRetainsCoreRequiredFieldsAndOptionalControlledAttachments() {
        var fields = new ArrayList<DynamicFormFieldDescriptor>();
        RequirementAnalysisEntityProvider.FIELDS.fields().forEach(field -> {
            String code = cn.hutool.core.util.StrUtil.toUnderlineCase(field.code()).toUpperCase(Locale.ROOT);
            fields.add(new DynamicFormFieldDescriptor(code, "Editor", false, field.required(), "any", null, null, null, List.of()));
            fields.add(new DynamicFormFieldDescriptor(code + "__ATTACHMENTS", "PmsFileArtifact", true, false, "controlled-file", null, null, null, List.of()));
        });
        var query = mock(DynamicFormRevisionPolicyQuery.class);
        when(query.action()).thenReturn(DynamicFormBusinessAction.REVISION_FROZEN_USE);
        when(query.requiredUsage()).thenReturn("PRE_04_REQUIREMENT_ANALYSIS");
        when(query.revisionFactVersion()).thenReturn(1);
        when(query.fields()).thenReturn(fields);
        assertTrue(provider.inspectRevisionCompatibility(query).allowed());
        var attachment = fields.get(1);
        fields.set(1, new DynamicFormFieldDescriptor(attachment.fieldKey(), "PmsFileArtifact", true, true, "controlled-file", null, null, null, List.of()));
        assertFalse(provider.inspectRevisionCompatibility(query).allowed());
        verifyNoInteractions(access, mapper);
    }

    @Test void oldInstanceCannotCreateSaveFreezeCopyOrModifyFiles() {
        for (var action : DynamicFormBusinessAction.values()) {
            if (Set.of(DynamicFormBusinessAction.READ, DynamicFormBusinessAction.FILE_READ).contains(action)) continue;
            assertFalse(provider.inspectInstanceOwnerPolicy(query(action)).allowed());
        }
        verifyNoInteractions(access,mapper);
    }
    @Test void oldReferenceReadUsesMigratedRevisionIdentityAndCurrentAuthorization() {
        var row = new RequirementAnalysisRevisionDO(); row.setId(42L); row.setVersion(3); row.setRevisionState("FROZEN");
        when(access.read(eq(42L),any())).thenReturn(row);
        assertTrue(provider.inspectInstanceOwnerPolicy(query(DynamicFormBusinessAction.FILE_READ)).allowed());
        when(access.read(eq(42L),any())).thenThrow(new IllegalStateException("permission revoked"));
        assertFalse(provider.inspectInstanceOwnerPolicy(query(DynamicFormBusinessAction.FILE_READ)).allowed());
    }
    private DynamicFormInstancePolicyQuery query(DynamicFormBusinessAction action) {
        return new DynamicFormInstancePolicyQuery(1L,7L,provider.providerKey(),
                new DynamicFormOwnerKey("SOL","REQUIREMENT_ANALYSIS","42"),901L,action);
    }
}
