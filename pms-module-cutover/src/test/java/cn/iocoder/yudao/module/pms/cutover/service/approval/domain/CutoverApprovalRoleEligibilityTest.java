package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CutoverApprovalRoleEligibilityTest {
    @Test
    void unrelatedRoleCannotBecomeAnApprovalCandidateGroup() {
        assertThrows(IllegalArgumentException.class,
                () -> new CutoverApprovalRoleCandidatePort.CandidateSet(1L, "super_admin", List.of()));
    }

    @ParameterizedTest
    @CsvSource({"SECOND_LINE,probSupporter", "RND,probRD"})
    void frozenSelectedUserCanActWithoutCurrentRoleLookup(String nodeCode, String roleCode) {
        var fixture = new Fixture(nodeCode, roleCode, true);
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 2L));
        verify(fixture.roles).requireActiveUser(new CutoverApprovalRoleCandidatePort.ActorQuery(1L, 2L));
        verify(fixture.roles, never()).inspectActor(any());
    }

    @ParameterizedTest
    @CsvSource({"SECOND_LINE,probSupporter", "RND,probRD"})
    void frozenSelectedUserSurvivesProjectScopeChange(String nodeCode, String roleCode) {
        var fixture = new Fixture(nodeCode, roleCode, false);
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 2L));
        verifyNoInteractions(fixture.scopes);
    }

    @ParameterizedTest
    @CsvSource({"SECOND_LINE,probSupporter", "RND,probRD"})
    void sharedRoleAllowsCurrentMembersWithoutAssigningAPerson(String nodeCode, String roleCode) {
        var fixture = new Fixture(nodeCode, roleCode, true, true);
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 2L));
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 3L));
        assertFalse(fixture.policy.eligible(fixture.root, fixture.node, 4L));
    }

    @ParameterizedTest
    @CsvSource({"SECOND_LINE,probSupporter", "RND,probRD"})
    void sharedRoleDoesNotRequireOrdinaryProjectScope(String nodeCode, String roleCode) {
        var fixture = new Fixture(nodeCode, roleCode, false, true);
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 2L));
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 3L));
    }

    @Test
    void newlyAddedCurrentRoleMemberCanActWithoutFrozenPersonnelPool() {
        var fixture = new Fixture("SECOND_LINE", "probSupporter", true, true);
        when(fixture.roles.inspectActor(new CutoverApprovalRoleCandidatePort.ActorQuery(1L, 4L))).thenReturn(
                List.of(new CutoverApprovalRoleCandidatePort.ActorMembership(
                        new CutoverApprovalRoleCandidatePort.RoleReference(1L, 20L, "probSupporter"),
                        new CutoverApprovalRoleCandidatePort.Candidate(4L, 20L, 2L, 1L))));
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 4L));
        verifyNoInteractions(fixture.scopes);
    }

    @ParameterizedTest
    @CsvSource({"SECOND_LINE,probSupporter", "RND,probRD"})
    void tenantRoleMembersCanActWithoutProjectScope(String nodeCode, String roleCode) {
        var fixture = new Fixture(nodeCode, roleCode, false, true);
        fixture.tenantRoleScope();
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 2L));
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 3L));
        verify(fixture.scopes, never()).inspect(any());
    }

    @Test
    void tenantRoleStillRejectsRevokedRoleNonMemberAndForeignTenant() {
        var fixture = new Fixture("SECOND_LINE", "probSupporter", false, true);
        when(fixture.roles.inspectActor(new CutoverApprovalRoleCandidatePort.ActorQuery(1L, 2L))).thenReturn(List.of());
        assertFalse(fixture.policy.eligible(fixture.root, fixture.node, 2L));
        assertFalse(fixture.policy.eligible(fixture.root, fixture.node, 4L));
        fixture.node.setTenantId(2L);
        assertFalse(fixture.policy.eligible(fixture.root, fixture.node, 3L));
        verifyNoInteractions(fixture.scopes);
    }

    @Test
    void unrelatedMemberChangesDoNotInvalidateCurrentActor() {
        var fixture = new Fixture("SECOND_LINE", "probSupporter", false, true);
        when(fixture.roles.inspectActor(new CutoverApprovalRoleCandidatePort.ActorQuery(1L, 2L))).thenReturn(
                List.of(new CutoverApprovalRoleCandidatePort.ActorMembership(
                        new CutoverApprovalRoleCandidatePort.RoleReference(1L, 20L, "probSupporter"),
                        new CutoverApprovalRoleCandidatePort.Candidate(2L, 20L, 9L, 7L))));
        assertTrue(fixture.policy.eligible(fixture.root, fixture.node, 2L));
        verifyNoInteractions(fixture.scopes);
        verify(fixture.roles, never()).inspectCandidates(any());
    }

    @Test
    void emptyCurrentRoleMembershipDoesNotGrantAnActor() {
        var fixture = new Fixture("RND", "probRD", false, true);
        doReturn(List.of()).when(fixture.roles).inspectActor(any());
        assertFalse(fixture.policy.eligible(fixture.root, fixture.node, 2L));
        verifyNoInteractions(fixture.scopes);
    }

    @Test void claimedRoleSurvivesRemovalButCannotBeTakenByAnotherMember() {
        var f = new Fixture("SECOND_LINE", "probSupporter", false, true);
        f.node.setCurrentApproverUserId(2L);
        doReturn(List.of()).when(f.roles).inspectActor(any());
        assertTrue(f.policy.eligible(f.root, f.node, 2L));
        assertFalse(f.policy.eligible(f.root, f.node, 3L));
        verify(f.roles, never()).inspectActor(any());
    }
    @Test void frozenPersonalAssignmentDoesNotRequeryProjectOrManager() {
        var f = new Fixture("SERVICE_MANAGER", "probSupporter", false);
        assertTrue(f.policy.eligible(f.root, f.node, 2L));
        assertFalse(f.policy.eligible(f.root, f.node, 3L));
        verifyNoInteractions(f.scopes);
    }
    @Test void disabledAssignedAccountCannotAct() {
        var f = new Fixture("SECOND_LINE", "probSupporter", false, true);
        f.node.setCurrentApproverUserId(2L);
        doThrow(new IllegalArgumentException("disabled")).when(f.roles).requireActiveUser(any());
        assertThrows(IllegalArgumentException.class, () -> f.policy.eligible(f.root, f.node, 2L));
    }
    private static final class Fixture {
        void tenantRoleScope() { node.setApprovalModeCode("ROLE"); }
        final CutoverApprovalRoleCandidatePort roles = mock(CutoverApprovalRoleCandidatePort.class);
        final CutoverApprovalProjectScopePort scopes = mock(CutoverApprovalProjectScopePort.class);
        final CutoverApprovalInstanceDO root = new CutoverApprovalInstanceDO();
        final CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();
        final CutoverApprovalEligibilityPolicy policy;

        Fixture(String nodeCode, String roleCode, boolean projectAllowed) {
            this(nodeCode, roleCode, projectAllowed, false);
        }

        Fixture(String nodeCode, String roleCode, boolean projectAllowed, boolean byRole) {
            root.setTenantId(1L); root.setProjectId(10L);
            node.setTenantId(1L); node.setNodeCode(nodeCode); node.setCurrentApproverUserId(2L);
            node.setApprovalModeCode(byRole ? "ROLE" : "PERSONAL");
            node.setCandidateRoleId(20L); node.setCandidateRoleCode(roleCode); node.setCandidateGroupKey("ROLE:20");
            if (byRole) node.setCurrentApproverUserId(null);
            when(roles.inspectActor(any())).thenAnswer(invocation -> {
                CutoverApprovalRoleCandidatePort.ActorQuery query = invocation.getArgument(0);
                if (query.actorId() != 2L && (query.actorId() != 3L || !byRole)) return List.of();
                return List.of(new CutoverApprovalRoleCandidatePort.ActorMembership(
                        new CutoverApprovalRoleCandidatePort.RoleReference(query.tenantId(), 20L, roleCode),
                        new CutoverApprovalRoleCandidatePort.Candidate(query.actorId(), 20L, 1L, 1L)));
            });
            when(scopes.inspect(any())).thenReturn(new CutoverApprovalProjectScopePort.ProjectScopeFact(
                    1L, 10L, 2L, "ACTION_VIEW", projectAllowed, 1L));
            policy = new CutoverApprovalEligibilityPolicy(roles);
        }
    }
}
