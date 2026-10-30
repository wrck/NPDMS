package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisDiscardAuthorizationTest {
    private final RequirementAnalysisMapper mapper = mock(RequirementAnalysisMapper.class);
    private final RequirementAnalysisAccess access = mock(RequirementAnalysisAccess.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);
    private final RequirementAnalysisEntityProvider provider = new RequirementAnalysisEntityProvider(mapper, access,
            mock(EntityExtensionApi.class), mock(EntityFormApi.class), mock(RequirementAnalysisRevisionFiles.class),
            audit, mock(EngineeringRuleReevaluationEvents.class));
    private final EntityActor actor = new EntityActor(1L, 9L, "discard");
    private final RevisionRef ref = new RevisionRef(new EntityRef(1L, "SOL", "REQUIREMENT_ANALYSIS", 50L), 60L);
    private final RequirementAnalysisRevisionDO draft = new RequirementAnalysisRevisionDO();
    @BeforeEach void setup() {
        draft.setId(60L); draft.setTenantId(1L); draft.setEntityId(50L); draft.setProjectId(20L);
        draft.setRevisionState("DRAFT"); draft.setVersion(3L); draft.setRevisionNo(1);
        when(access.read(60L, actor)).thenReturn(draft);
        when(access.lock(60L, 3, actor, null, true)).thenReturn(draft);
        when(mapper.discardDraft(any())).thenReturn(1);
    }
    @Test void discardRechecksSourceAuthorizationAndVersionBeforeWriting() {
        provider.discard(ref, actor);
        var ordered = inOrder(access, mapper);
        ordered.verify(access).lock(60L, 3, actor, null, true);
        ordered.verify(mapper).discardDraft(any());
    }
    @Test void revocationAfterReadMakesDiscardAndSuccessAuditZeroWrite() {
        when(access.lock(60L, 3, actor, null, true)).thenThrow(new IllegalStateException("regional scope revoked"));
        assertThrows(RuntimeException.class, () -> provider.discard(ref, actor));
        verify(mapper, never()).discardDraft(any()); verifyNoInteractions(audit);
    }
    @Test void frozenHistoryRemainsImmutable() {
        draft.setRevisionState("FROZEN");
        when(access.lock(60L, 3, actor, null, true)).thenThrow(new IllegalStateException("frozen"));
        assertThrows(RuntimeException.class, () -> provider.discard(ref, actor));
        verify(mapper, never()).discardDraft(any()); verifyNoInteractions(audit);
    }
}
