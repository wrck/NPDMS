package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisRevisionFilePolicyContextTest {
    @Test void coreAttachmentSetsDoNotResolveUnusedFormLayout() {
        var fixture = new Fixture();
        var queries = RequirementAnalysisFields.attachmentKeys().stream().map(fixture::read).toList();
        var facts = fixture.policy.inspectReferenceSets(queries);
        assertEquals(queries.size(), facts.size());
        assertTrue(facts.values().stream().allMatch(fact -> fact.allowed()));
        verify(fixture.access).read(eq(3L), any());
        verifyNoInteractions(fixture.forms);
    }

    @Test void mixedCoreAndCustomSetsLoadLayoutOnceInEitherOrder() {
        for (boolean coreFirst : List.of(true, false)) {
            var fixture = new Fixture();
            var layout = new EntityFormApi.Layout(null, 1L, 1, 1, "FORM_CREATE", "1", "1", "{}", "[]",
                    List.of(new DynamicFormFieldDescriptor("custom", "PmsFileArtifact", true, false, null, null, null, null, List.of())));
            when(fixture.forms.layout(any(), any())).thenReturn(layout);
            var core = fixture.read("NETWORK_TOPOLOGY__ATTACHMENTS");
            var custom = fixture.read("custom");
            var unknown = fixture.read("unknown");
            var queries = coreFirst ? List.of(core, custom, unknown) : List.of(custom, core, unknown);
            var facts = fixture.policy.inspectReferenceSets(queries);
            assertTrue(facts.get(core).allowed());
            assertTrue(facts.get(custom).allowed());
            assertFalse(facts.get(unknown).allowed());
            verify(fixture.access).read(eq(3L), any());
            verify(fixture.forms).layout(any(), any());
        }
    }

    @Test void missingCustomLayoutDoesNotAllowTheCustomSlot() {
        var fixture = new Fixture();
        var core = fixture.read("NETWORK_TOPOLOGY__ATTACHMENTS");
        var custom = fixture.read("custom");
        var facts = fixture.policy.inspectReferenceSets(List.of(core, custom));
        assertTrue(facts.get(core).allowed());
        assertFalse(facts.get(custom).allowed());
        verify(fixture.forms).layout(any(), any());
    }

    @Test void coreLockRevalidatesEverySetAndStillRejectsStaleScopes() {
        var fixture = new Fixture();
        when(fixture.mapper.lockRevision(any())).thenReturn(fixture.row);
        var queries = RequirementAnalysisFields.attachmentKeys().stream().map(fixture::read).toList();
        for (var query : queries) assertTrue(fixture.policy.lockAndRevalidateReferenceSet(
                new FileBusinessObjectReferenceSetRevalidationQuery(1L, 9L, query.key(), FileActionCodes.READ, 3L)).allowed());
        verify(fixture.access, times(queries.size())).read(eq(3L), any());
        verify(fixture.mapper, times(queries.size())).lockRevision(any());
        verifyNoInteractions(fixture.forms);
        var stale = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class, () -> fixture.policy.lockAndRevalidateReferenceSet(
                new FileBusinessObjectReferenceSetRevalidationQuery(1L, 9L, queries.getFirst().key(), FileActionCodes.READ, 4L)));
        assertEquals(cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_VERSION_CONFLICT.getCode(), stale.getCode());
    }

    @Test void coreSlotsStillFailWhenCurrentAccessIsRevoked() {
        var fixture = new Fixture();
        when(fixture.access.read(eq(3L), any())).thenThrow(new SecurityException("revoked"));
        assertThrows(SecurityException.class, () -> fixture.policy.inspectReferenceSets(
                List.of(fixture.read("NETWORK_TOPOLOGY__ATTACHMENTS"))));
        assertThrows(SecurityException.class, () -> fixture.policy.lockAndRevalidateReferenceSet(
                new FileBusinessObjectReferenceSetRevalidationQuery(1L, 9L,
                        fixture.read("NETWORK_TOPOLOGY__ATTACHMENTS").key(), FileActionCodes.READ, 3L)));
        verifyNoInteractions(fixture.forms, fixture.mapper);
    }

    @Test void customSchemaFailureIsNotHidden() {
        var fixture = new Fixture();
        when(fixture.forms.layout(any(), any())).thenThrow(new IllegalStateException("schema unavailable"));
        assertThrows(IllegalStateException.class, () -> fixture.policy.inspectReferenceSets(
                List.of(fixture.read("NETWORK_TOPOLOGY__ATTACHMENTS"), fixture.read("custom"))));
    }

    @Test void coreWritesStillRequireCurrentManagerAndMutableRevision() {
        var fixture = new Fixture();
        var upload = new FileBusinessObjectPolicyQuery(1L, 9L, "SOL", "REQUIREMENT_ANALYSIS_REVISION", "3",
                FormAttachmentPolicy.PURPOSE_PREFIX + "NETWORK_TOPOLOGY__ATTACHMENTS", "file", FileActionCodes.UPLOAD);
        assertFalse(fixture.policy.inspect(upload).allowed());
        when(fixture.access.isManager(eq(20L), any())).thenReturn(true);
        assertTrue(fixture.policy.inspect(upload).allowed());
        fixture.row.setRevisionState("FROZEN");
        assertFalse(fixture.policy.inspect(upload).allowed());
        verifyNoInteractions(fixture.forms, fixture.mapper);
    }

    private static class Fixture {
        final EntityFormApi forms = mock(EntityFormApi.class);
        final RequirementAnalysisAccess access = mock(RequirementAnalysisAccess.class);
        final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
        final RequirementAnalysisRevisionDO row = new RequirementAnalysisRevisionDO();
        final RequirementAnalysisRevisionFilePolicy policy;
        @SuppressWarnings("unchecked")
        Fixture() {
            row.setId(3L); row.setEntityId(2L); row.setTenantId(1L); row.setProjectId(20L); row.setRevisionState("DRAFT");
            when(access.read(eq(3L), any())).thenReturn(row);
            ObjectProvider<EntityFormApi> provider = mock(ObjectProvider.class);
            when(provider.getObject()).thenReturn(forms);
            policy = new RequirementAnalysisRevisionFilePolicy(mapper, access, provider);
        }
        FileBusinessObjectReferenceSetQuery read(String field) {
            return new FileBusinessObjectReferenceSetQuery(1L, 9L,
                    new FileReferenceSetKey("SOL", "REQUIREMENT_ANALYSIS_REVISION", "3", FormAttachmentPolicy.PURPOSE_PREFIX + field), FileActionCodes.READ);
        }
    }

    @Test void fileRegistryCanInitializeBeforeFormsAndResolvesLayoutWhenCheckingFiles() {
        var forms = mock(EntityFormApi.class);
        var access = mock(RequirementAnalysisAccess.class);
        var row = new RequirementAnalysisRevisionDO(); row.setId(3L); row.setEntityId(2L); row.setTenantId(1L);
        when(access.read(eq(3L), any())).thenReturn(row);
        try (var context = new AnnotationConfigApplicationContext()) {
            // The form service reaches the file registry through its dynamic-form dependency.
            context.registerBean(EntityFormApi.class, () -> {
                context.getBean(FileBusinessObjectPolicyRegistry.class);
                return forms;
            });
            context.registerBean(RequirementAnalysisMapper.class, () -> mock(RequirementAnalysisMapper.class));
            context.registerBean(RequirementAnalysisAccess.class, () -> access);
            context.register(FileBusinessObjectPolicyRegistry.class, RequirementAnalysisRevisionFilePolicy.class);
            assertDoesNotThrow(context::refresh);
            verifyNoInteractions(forms);
            var policy = context.getBean(RequirementAnalysisRevisionFilePolicy.class);
            var result = policy.inspect(new FileBusinessObjectPolicyQuery(1L, 9L, "SOL", "REQUIREMENT_ANALYSIS_REVISION",
                    "3", FormAttachmentPolicy.PURPOSE_PREFIX + "evidence", "ref", FileActionCodes.READ));
            assertFalse(result.allowed());
            verify(forms).layout(EntityDataRef.revision(row.revisionRef()), new EntityActor(1L, 9L, null));
            var topology = policy.inspect(new FileBusinessObjectPolicyQuery(1L, 9L, "SOL", "REQUIREMENT_ANALYSIS_REVISION",
                    "3", FormAttachmentPolicy.PURPOSE_PREFIX + "NETWORK_TOPOLOGY__ATTACHMENTS", "ref", FileActionCodes.READ));
            assertTrue(topology.allowed());
            assertTrue(topology.allowedMediaTypes().contains("application/vnd.ms-visio.drawing"));
            assertTrue(topology.allowedMediaTypes().contains("application/vnd.visio"));
            assertFalse(result.allowedMediaTypes().contains("application/vnd.ms-visio.drawing"));
        }
    }
}
