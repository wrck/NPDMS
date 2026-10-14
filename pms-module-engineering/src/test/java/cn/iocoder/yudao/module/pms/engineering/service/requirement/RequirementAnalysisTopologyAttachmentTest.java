package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisTopologyAttachmentTest {
    private final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
    private final RequirementAnalysisAccess access = mock(RequirementAnalysisAccess.class);
    private final EntityFormApi forms = mock(EntityFormApi.class);
    private final FileArtifactApi fileApi = mock(FileArtifactApi.class);
    private final PlatformDeliveryMaterialApi materials = mock(PlatformDeliveryMaterialApi.class);
    private final RequirementAnalysisRevisionFiles files = new RequirementAnalysisRevisionFiles(fileApi, access, forms);
    private final RequirementAnalysisEntityProvider provider = new RequirementAnalysisEntityProvider(mapper, access,
            mock(EntityExtensionApi.class), forms, files, mock(OperationAuditApi.class), mock(EngineeringRuleReevaluationEvents.class));
    private final EntityActor actor = new EntityActor(1L, 7L, "topology-completion");
    private final RequirementAnalysisRevisionDO row = new RequirementAnalysisRevisionDO();
    private RevisionRef ref;

    @BeforeEach void setup() {
        row.setTenantId(1L); row.setId(11L); row.setEntityId(9L); row.setProjectId(20L);
        row.setRevisionState("DRAFT"); row.setRevisionNo(1); row.setVersion(1L);
        row.setProjectBackground("<p>项目背景</p>"); row.setProjectObjective("<p>项目目标</p>");
        ref = row.revisionRef();
        when(access.lock(11L, 1, actor, null, true)).thenReturn(row);
        when(access.read(11L, actor)).thenReturn(row);
        when(mapper.freeze(any())).thenReturn(1);
        when(mapper.selectRevision(any())).thenReturn(row);
        ReflectionTestUtils.setField(files, "materials", materials);
    }

    private FileReferenceSetFact topology(String availability) {
        var file = new FileArtifactVersionFact(31L, 1, "topology", "DYNAMIC_FORM_ATTACHMENT", "topology.png",
                3L, "image/png", "digest", availability, "ACTIVE", null, 1L);
        return new FileReferenceSetFact(new FileReferenceSetKey("SOL", "REQUIREMENT_ANALYSIS_REVISION", "11",
                "FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS"), 1L, List.of(file));
    }

    @Test void validTopologyAttachmentCompletesWithoutTopologyText() {
        var set = topology("AVAILABLE");
        when(fileApi.inspectReferenceSets(any())).thenReturn(List.of(set));
        when(fileApi.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));
        assertDoesNotThrow(() -> provider.freeze(ref, 1, actor));
        verify(mapper).freeze(any());
        verify(materials).registerNativeSourceFile(set.activeFacts().getFirst());
        assertFalse(provider.fields().stream().filter(field -> field.code().equals("networkTopology")).findFirst().orElseThrow().required());
    }

    @Test void topologyTextCannotReplaceMissingAttachment() {
        row.setNetworkTopology("<p>历史拓扑说明</p>");
        when(fileApi.inspectReferenceSets(any())).thenReturn(List.of());
        assertThrows(Exception.class, () -> provider.freeze(ref, 1, actor));
        verify(mapper, never()).freeze(any()); verifyNoInteractions(materials);
    }

    @Test void oldFrozenResultFileRevalidationDoesNotRetroactivelyRequireTopology() {
        row.setRevisionState("FROZEN");
        when(fileApi.inspectReferenceSets(any())).thenReturn(List.of());
        assertDoesNotThrow(() -> files.lockForFreeze(ref, actor));
        verifyNoInteractions(materials);
    }

    @Test void unavailableTopologyDoesNotCompleteOrRegisterMaterials() {
        var set = topology("UNAVAILABLE");
        when(fileApi.inspectReferenceSets(any())).thenReturn(List.of(set));
        when(fileApi.lockAndRevalidateReferenceSets(any())).thenReturn(List.of(set));
        assertThrows(Exception.class, () -> provider.freeze(ref, 1, actor));
        verify(mapper, never()).freeze(any()); verifyNoInteractions(materials);
    }

    @Test void changedFileVersionFailsBeforeFreezing() {
        when(fileApi.inspectReferenceSets(any())).thenReturn(List.of(topology("AVAILABLE")));
        when(fileApi.lockAndRevalidateReferenceSets(any())).thenThrow(new IllegalStateException("file version changed"));
        assertThrows(Exception.class, () -> provider.freeze(ref, 1, actor));
        verify(mapper, never()).freeze(any()); verifyNoInteractions(materials);
    }

    @Test void backgroundAndObjectiveRemainRequired() {
        row.setProjectObjective("<p>&nbsp;</p>");
        assertThrows(Exception.class, () -> provider.freeze(ref, 1, actor));
        verify(mapper, never()).freeze(any()); verifyNoInteractions(fileApi, materials);
    }
}
