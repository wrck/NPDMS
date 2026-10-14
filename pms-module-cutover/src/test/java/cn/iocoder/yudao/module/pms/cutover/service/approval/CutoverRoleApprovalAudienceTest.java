package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRoleApprovalSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverRoleApprovalAudienceTest {
    @Test void currentEnabledRoleMemberCanReadWithoutOrdinaryProjectScope() {
        Fixture f = new Fixture();
        var grant = f.audience.inspect(query(1L, 8L, 20L));
        assertNotNull(grant);
        assertEquals(20L, grant.approvalId());
        verifyNoInteractions(f.scopes, f.managers);
    }
    @Test void wrongTenantNonMemberRemovedOrDisabledMemberAndMissingPermissionAreDenied() {
        Fixture f = new Fixture();
        assertNull(f.audience.inspect(query(2L, 8L, 20L)));
        assertNull(f.audience.inspect(query(1L, 9L, 20L)));
        doReturn(List.of()).when(f.roles).inspectActor(any());
        doReturn(List.of()).when(f.roles).lockAndInspectActor(any());
        assertNull(f.audience.inspect(query(1L, 8L, 20L))); // SYS filters removed/disabled users and roles.
        Fixture missingPermission = new Fixture();
        when(missingPermission.permissions.hasAnyPermissions(8L, "pms:cutover-task:query-approval")).thenReturn(false);
        assertNull(missingPermission.audience.inspect(query(1L, 8L, 20L)));
        verifyNoInteractions(missingPermission.tasks, missingPermission.instances, missingPermission.nodes);
    }
    @Test void lockedActorRevocationAndWrongApprovalIdCannotProduceGrant() {
        Fixture f = new Fixture();
        var reference = file("CUTOVER_PLAN", "10", "cutover-plan:50:2");
        assertNotNull(f.audience.lockAndRevalidateFile(new CutoverRoleApprovalAudience.FileQuery(query(1L, 8L, 20L), reference)));
        doReturn(List.of(new CutoverApprovalRoleCandidatePort.ActorMembership(
                new CutoverApprovalRoleCandidatePort.RoleReference(1L, 2L, "probSupporter"),
                new CutoverApprovalRoleCandidatePort.Candidate(8L, 2L, 9L, 10L)))).when(f.roles).lockAndInspectActor(any());
        assertNotNull(f.audience.lockAndRevalidateFile(new CutoverRoleApprovalAudience.FileQuery(query(1L, 8L, 20L), reference)));
        doReturn(List.of()).when(f.roles).lockAndInspectActor(any());
        assertNull(f.audience.lockAndRevalidateFile(new CutoverRoleApprovalAudience.FileQuery(query(1L, 8L, 20L), reference)));
        assertNull(f.audience.lockAndRevalidateFile(new CutoverRoleApprovalAudience.FileQuery(query(1L, 8L, 21L), reference)));
    }
    @Test void nodeVersionProgressKeepsCurrentTodoGrantButReplacementRemovesTemporaryRead() {
        Fixture f = new Fixture();
        var reference = file("CUTOVER_PLAN", "10", "cutover-plan:50:2");
        var access = query(1L, 8L, 20L);
        assertEquals(20L, f.audience.inspectFile(new CutoverRoleApprovalAudience.FileQuery(access, reference)).approvalId());
        f.root.setVersion(4L);
        assertEquals(20L, f.audience.lockAndRevalidateFile(new CutoverRoleApprovalAudience.FileQuery(access, reference)).approvalId());
        clearInvocations(f.evidence);
        f.root.setReplacementApprovalInstanceId(21L);
        assertNull(f.audience.inspect(access));
        assertNull(f.audience.inspectFile(new CutoverRoleApprovalAudience.FileQuery(access, reference)));
        assertNull(f.audience.lockAndRevalidateFile(new CutoverRoleApprovalAudience.FileQuery(access, reference)));
        verifyNoInteractions(f.evidence);
    }
    @Test void onlyCurrentPendingNodeOnUnheldUnreplacedPendingRootCanReadFrozenFiles() {
        for (String state : List.of("WAITING", "APPROVED", "REJECTED", "CANCELLED")) {
            Fixture f = new Fixture();
            f.node.setStatusCode(state);
            assertTemporaryReadDenied(f);
        }
        for (String state : List.of("APPROVED", "REJECTED", "PAUSED_SOURCE_INVALIDATED", "CANCELLED")) {
            Fixture f = new Fixture();
            f.root.setStatusCode(state);
            assertTemporaryReadDenied(f);
        }
        Fixture held = new Fixture(); held.root.setHoldReasonCode("APPROVER_UNAVAILABLE");
        assertTemporaryReadDenied(held);
        Fixture replaced = new Fixture(); replaced.root.setReplacementApprovalInstanceId(21L);
        assertTemporaryReadDenied(replaced);
        Fixture differentNode = new Fixture(); differentNode.root.setCurrentNodeNo(2);
        assertTemporaryReadDenied(differentNode);
    }
    private static void assertTemporaryReadDenied(Fixture f) {
        var access = query(1L, 8L, 20L);
        var request = new CutoverRoleApprovalAudience.FileQuery(access,
                file("CUTOVER_PLAN", "10", "cutover-plan:50:2"));
        assertNull(f.audience.inspect(access));
        assertNull(f.audience.inspectFile(request));
        assertNull(f.audience.lockAndRevalidateFile(request));
        verifyNoInteractions(f.evidence);
    }
    @Test void frozenUploadedPlanTopologyAndManualEvidenceRequireExactReferences() {
        String source = sourceSnapshot();
        var grant = new CutoverRoleApprovalAudience.Grant(20L, source);
        assertFalse(grant.references(file("CUTOVER_PLAN", "10", "plan-exact")));
        String online = JsonUtils.toJsonString(java.util.Map.of("plan", java.util.Map.of(
                "planRevisionId", 50, "planVersion", 2, "content", java.util.Map.of(
                        "editMode", "ONLINE_TEMPLATE_STANDARD", "overview", java.util.Map.of(
                                "preTopologyFile", frozenFile(61L, 1, "plan-exact")),
                        "steps", List.of(), "riskMitigations", List.of(), "supportArrangements", List.of()))));
        var onlineGrant = new CutoverRoleApprovalAudience.Grant(20L, online);
        assertTrue(onlineGrant.references(file("CUTOVER_PLAN", "10", "plan-exact")));
        assertFalse(onlineGrant.references(file("CUTOVER_PLAN", "10", "cutover-plan:50:2")));
        assertTrue(grant.references(file("CUTOVER_PLAN", "10", "cutover-plan:50:2")));
        assertFalse(grant.references(file("CUTOVER_PLAN", "10", "cutover-plan:50:1")));
        assertFalse(grant.references(file("CUTOVER_PLAN", "10", "cutover-plan:51:2")));
        assertFalse(grant.references(file("CUTOVER_PLAN", "10", "cutover-plan:50:3")));
        assertFalse(grant.references(file("CUTOVER_PLAN", "10", "other-ref")));
        assertTrue(grant.references(file("CUTOVER_CHECKLIST_ITEM", "70", "item-exact")));
        assertFalse(grant.references(file("CUTOVER_CHECKLIST_ITEM", "71", "item-exact")));
        assertFalse(grant.references(file("CUTOVER_CHECKLIST_ITEM", "70", "other-ref")));
        assertFalse(grant.references(file("CUTOVER_CLOSURE", "70", "item-exact")));
    }
    @Test void existingEvidenceApiRevalidatesTheExactFrozenFileIdentity() {
        Fixture f = new Fixture();
        var reference = file("CUTOVER_PLAN", "10", "cutover-plan:50:2");
        assertNotNull(f.audience.inspectFile(new CutoverRoleApprovalAudience.FileQuery(query(1L, 8L, 20L), reference)));
        var captured = org.mockito.ArgumentCaptor.forClass(FileEvidenceApi.Query.class);
        verify(f.evidence).lockAndRevalidate(captured.capture());
        var factQuery = captured.getValue();
        assertEquals(1L, factQuery.tenantId());
        assertEquals(60L, factQuery.artifactId());
        assertEquals(1, factQuery.versionNo());
        assertEquals("CUT", factQuery.ownerContext());
        assertEquals("CUTOVER_PLAN", factQuery.objectType());
        assertEquals("10", factQuery.objectId());
        assertEquals("FULL_PLAN", factQuery.purposeCode());
        assertEquals("cutover-plan:50:2", factQuery.referenceKey());
        assertEquals("a".repeat(64), factQuery.sha256());
    }
    @Test void invalidVersionAvailabilityOrBindingFactCannotGrantFrozenFileRead() {
        for (String reason : List.of("VERSION_CHANGED", "FILE_UNAVAILABLE", "REFERENCE_MISMATCH")) {
            Fixture f = new Fixture();
            when(f.evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(false, reason, 1, 1, 1));
            var request = new CutoverRoleApprovalAudience.FileQuery(query(1L, 8L, 20L),
                    file("CUTOVER_PLAN", "10", "cutover-plan:50:2"));
            assertNull(f.audience.inspectFile(request));
            assertNull(f.audience.lockAndRevalidateFile(request));
        }
    }
    @Test void unfrozenKeyAndUnqualifiedActorCannotQueryEvidence() {
        Fixture f = new Fixture();
        assertNull(f.audience.inspectFile(new CutoverRoleApprovalAudience.FileQuery(query(1L, 8L, 20L),
                file("CUTOVER_PLAN", "10", "unfrozen-ref"))));
        assertNull(f.audience.inspectFile(new CutoverRoleApprovalAudience.FileQuery(query(1L, 9L, 20L),
                file("CUTOVER_PLAN", "10", "cutover-plan:50:2"))));
        verifyNoInteractions(f.evidence);
    }
    private static java.util.Map<String, Object> frozenFile(Long artifact, Integer version, String key) {
        return java.util.Map.of("artifactId", artifact, "versionNo", version, "referenceKey", key,
                "fileFactVersion", java.util.Map.of("artifactVersion", 1, "referenceVersion", 1, "availabilityVersion", 1),
                "scopeVersion", 30L, "sha256", "a".repeat(64));
    }
    private static String sourceSnapshot() {
        return JsonUtils.toJsonString(java.util.Map.of(
                "plan", java.util.Map.of("planRevisionId", 50, "planVersion", 2,
                        "content", java.util.Map.of("editMode", "FULL_FILE_UPLOAD", "ownershipConfirmed", true,
                                "fileArtifactFact", frozenFile(60L, 1, "cutover-plan:50:2"))),
                "riskItems", List.of(java.util.Map.of("checklistItemId", 70, "answerSnapshot",
                        JsonUtils.toJsonString(java.util.Map.of("file", frozenFile(80L, 1, "item-exact"))))),
                "businessSurveyItems", List.of()));
    }
    private static CutoverRoleApprovalAudience.Query query(Long tenant, Long actor, Long approvalId) {
        return new CutoverRoleApprovalAudience.Query(tenant, actor, 10L, approvalId);
    }
    private static CutoverRoleApprovalAudience.FileReference file(String type, String object, String key) {
        return new CutoverRoleApprovalAudience.FileReference(type, object, key);
    }
    private static final class Fixture {
        final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        final CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        final CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        final CutoverApprovalRoleCandidatePort roles = mock(CutoverApprovalRoleCandidatePort.class);
        final PermissionApi permissions = mock(PermissionApi.class);
        final CutoverApprovalProjectScopePort scopes = mock(CutoverApprovalProjectScopePort.class);
        final ProjectCutoverServiceManagerPort managers = mock(ProjectCutoverServiceManagerPort.class);
        final CutoverApprovalRoleCandidatePort.Candidate candidate = new CutoverApprovalRoleCandidatePort.Candidate(8L, 2L, 1L, 1L);
        final CutoverApprovalRoleCandidatePort.CandidateSet current = new CutoverApprovalRoleCandidatePort.CandidateSet(1L, "probSupporter", List.of(candidate));
        final CutoverApprovalInstanceDO root = new CutoverApprovalInstanceDO();
        final CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();
        final FileEvidenceApi evidence = mock(FileEvidenceApi.class);
        final CutoverRoleApprovalAudience audience;
        Fixture() {
            CutoverTaskDO task = new CutoverTaskDO(); task.setId(10L); task.setTenantId(1L);
            root.setId(20L); root.setTenantId(1L); root.setTaskId(10L); root.setProjectId(30L); root.setVersion(3L); root.setSourceSnapshot(sourceSnapshot());
            root.setStatusCode("PENDING"); root.setCurrentNodeNo(1);
            node.setTenantId(1L); node.setNodeNo(1); node.setStatusCode("PENDING"); node.setNodeCode("SECOND_LINE");
            node.setApprovalInstanceId(root.getId());
            cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalNodeFacts.apply(node,
                    cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalNodeFacts.role(
                            new CutoverApprovalRoleCandidatePort.RoleReference(1L, 2L, "probSupporter")));
            when(tasks.selectById(10L)).thenReturn(task);
            when(instances.selectCurrentByTask(any())).thenReturn(root);
            when(instances.selectByIdForUpdate(any())).thenAnswer(call -> {
                var lock = (cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalInstanceLockQuery) call.getArgument(0);
                return Long.valueOf(20L).equals(lock.approvalInstanceId()) ? root : null;
            });
            when(instances.history(any())).thenReturn(List.of(root));
            when(nodes.all(any())).thenReturn(List.of(node));
            when(permissions.hasAnyPermissions(anyLong(), eq("pms:cutover-task:query-approval"))).thenReturn(true);
            when(roles.inspectCandidates(new CutoverApprovalRoleCandidatePort.RoleQuery(1L, "probSupporter"))).thenReturn(current);
            when(roles.lockAndRevalidate(any())).thenReturn(new CutoverApprovalRoleCandidatePort.CandidateRevalidation(
                    CutoverApprovalRoleCandidatePort.Revalidation.VALID, current));
            when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true, null, 1, 1, 1));
            var memberships = List.of(new CutoverApprovalRoleCandidatePort.ActorMembership(
                    new CutoverApprovalRoleCandidatePort.RoleReference(1L, 2L, "probSupporter"), candidate));
            when(roles.inspectActor(any())).thenAnswer(invocation -> {
                CutoverApprovalRoleCandidatePort.ActorQuery query = invocation.getArgument(0);
                return query.tenantId() == 1L && query.actorId() == 8L ? memberships : List.of();
            });
            when(roles.lockAndInspectActor(any())).thenAnswer(invocation -> roles.inspectActor(invocation.getArgument(0)));
            audience = new CutoverRoleApprovalAudience(tasks, instances, nodes, managers, roles, scopes, permissions, evidence);
            var storage = mock(CutoverApprovalStorage.class);
            when(storage.sourceView(root)).thenReturn(JsonUtils.parseTree(sourceSnapshot()));
            org.springframework.test.util.ReflectionTestUtils.setField(audience, "storage", storage);
        }
    }
}
