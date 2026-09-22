package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisRevisionFileBatchTest {
    @Test void oneOwnerReadPerBatchRetainsFieldAuthorizationAndDoesNotCacheTheNextRequest() {
        var forms = mock(EntityFormApi.class);
        var access = mock(RequirementAnalysisAccess.class);
        @SuppressWarnings("unchecked") var lazy = (ObjectProvider<EntityFormApi>) mock(ObjectProvider.class);
        when(lazy.getObject()).thenReturn(forms);
        var row = new RequirementAnalysisRevisionDO();
        row.setId(3L); row.setEntityId(2L); row.setTenantId(1L); row.setRevisionState("FROZEN");
        when(access.read(eq(3L), any())).thenReturn(row);
        var fields = java.util.stream.IntStream.range(0, 11).mapToObj(index -> new DynamicFormFieldDescriptor(
                "file" + index, "PmsFileArtifact", true, false, "controlled-file", null, null, null, List.of())).toList();
        when(forms.layout(any(), any())).thenReturn(new EntityFormApi.Layout(null, 1L, 1, 1,
                "engine", "designer", "renderer", "{}", "[]", fields));
        var policy = new RequirementAnalysisRevisionFilePolicy(mock(RequirementAnalysisMapper.class), access, lazy);
        var queries = new ArrayList<>(fields.stream().map(field -> query(1L, 7L, field.fieldKey())).toList());
        queries.add(query(1L, 7L, "not-a-file"));
        var result = policy.inspectReferenceSets(queries);
        assertEquals(11, result.values().stream().filter(FileBusinessObjectPolicyFact::allowed).count());
        assertFalse(result.get(queries.getLast()).allowed());
        verify(access, times(1)).read(eq(3L), any());
        verify(forms, times(1)).layout(any(), any());
        policy.inspectReferenceSets(List.of(query(1L, 8L, "file0"), query(2L, 7L, "file0")));
        verify(forms, times(3)).layout(any(), any());
        when(access.read(eq(3L), any())).thenThrow(new IllegalStateException("authorization revoked"));
        assertThrows(IllegalStateException.class, () -> policy.inspectReferenceSets(List.of(queries.getFirst())));
    }
    @Test void baseBusinessAttachmentsRemainAvailableWithoutAnyPresentationBinding() {
        var forms = mock(EntityFormApi.class);
        var access = mock(RequirementAnalysisAccess.class);
        @SuppressWarnings("unchecked") var lazy = (ObjectProvider<EntityFormApi>) mock(ObjectProvider.class);
        when(lazy.getObject()).thenReturn(forms);
        var row = new RequirementAnalysisRevisionDO(); row.setId(3L); row.setEntityId(2L); row.setTenantId(1L);
        row.setProjectId(9L); row.setRevisionState("DRAFT");
        when(access.read(eq(3L), any())).thenReturn(row);
        when(access.isManager(eq(9L),any())).thenReturn(true);
        var policy = new RequirementAnalysisRevisionFilePolicy(mock(RequirementAnalysisMapper.class), access, lazy);
        assertTrue(policy.inspectReferenceSet(query(1L,7L,"PROJECT_BACKGROUND__ATTACHMENTS")).allowed());
        assertFalse(policy.inspectReferenceSet(query(1L,7L,"UNREGISTERED__ATTACHMENTS")).allowed());
        row.setRevisionState("FROZEN");
        assertTrue(policy.inspectReferenceSet(query(1L,7L,"PROJECT_BACKGROUND__ATTACHMENTS")).allowed());
    }

    private FileBusinessObjectReferenceSetQuery query(Long tenant, Long actor, String field) {
        return new FileBusinessObjectReferenceSetQuery(tenant, actor,
                new FileReferenceSetKey("SOL", "REQUIREMENT_ANALYSIS_REVISION", "3", "FORM_FIELD_ATTACHMENT/" + field), "READ");
    }
}
