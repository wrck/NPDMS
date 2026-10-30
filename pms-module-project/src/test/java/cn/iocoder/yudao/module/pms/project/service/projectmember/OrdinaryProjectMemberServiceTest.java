package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectMemberPageQuery;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmember.OrdinaryProjectMemberService.*;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class OrdinaryProjectMemberServiceTest {
    @Mock ProjectManualCreationService projects;
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ActiveUserSelectionApi users;
    @Mock DeptApi departments;
    @Mock PermissionCommonApi permissions;
    @Mock ProjectAuthorizationGuard authorization;
    @Mock PlatformCommandExecutionApi commands;
    @Mock ProjectManagerMemberApplicationService projectManagers;
    @Mock cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManagerAssignmentApplicationService serviceManagers;
    @InjectMocks OrdinaryProjectMemberService service;
    ProjectMasterDO project;
    PlatformCommandExecutionApi.SuccessFacts successFacts;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(1L);
        project = new ProjectMasterDO(); project.setId(100L); project.setTenantId(1L);
        project.setLifecycleStatus("ACTIVE"); project.setVersion(0L); project.setManagerId(55L);
        project.setAssignmentStatus("ASSIGNED"); project.setCurrentStage("S2"); project.setCompanyId(8L);
        lenient().when(projectMapper.selectById(100L)).thenReturn(project);
        lenient().when(projectMapper.selectByIdForUpdate(100L)).thenReturn(project);
        lenient().when(permissions.hasAnyPermissions(7L, OrdinaryProjectMemberService.WRITE_PERMISSION)).thenReturn(true);
        lenient().when(users.page(any())).thenReturn(new PageResult<>(
                List.of(new ActiveUserSelectionApi.User(40L, "engineer", "普通人员", 25L)), 1L));
        lenient().when(departments.getDept(25L)).thenReturn(new DeptRespDTO().setId(25L).setCode("OTHER-DEPT").setName("其他部门"));
        lenient().when(memberMapper.selectActiveMemberIdentityForUpdate(any())).thenReturn(List.of());
        lenient().when(memberMapper.insert(any(ProjectMemberAssignmentDO.class))).thenAnswer(call -> {
            ((ProjectMemberAssignmentDO) call.getArgument(0)).setId(90L); return 1;
        });
        lenient().when(memberMapper.updateById(any(ProjectMemberAssignmentDO.class))).thenReturn(1);
        lenient().when(projectMapper.incrementVersionIfMatch(eq(100L), anyLong())).thenReturn(1);
        lenient().when(commands.execute(any(), any(), any(), any(), any())).thenAnswer(call -> {
            var result = ((Supplier<Object>) call.getArgument(3)).get();
            successFacts = ((java.util.function.Function<Object, PlatformCommandExecutionApi.SuccessFacts>) call.getArgument(4)).apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void externalEndUserNeedsNoAccountAndNeverBecomesExecutionIdentity() {
        var external = new MemberValues(null, "END_USER", "", "", false, null,
                ExternalPerson.builder().name("最终用户甲").companyName("客户单位").mobile("123").email("a@example.test")
                        .department(" 客户建设部 ").title(" 主管 ").build());
        service.mutate(new Command(100L, 0L, Action.ADD, null, external, "加入", "external-add"), actor());
        var saved = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).insert(saved.capture());
        assertNull(saved.getValue().getUserId());
        assertEquals(Boolean.FALSE, saved.getValue().getAuthorizationConfirmed());
        assertEquals("最终用户甲", saved.getValue().getMemberName());
        assertEquals("客户单位", saved.getValue().getCompanyName());
        assertEquals("客户建设部", saved.getValue().getDepartmentName());
        assertEquals("主管", saved.getValue().getTitle());
        assertNull(saved.getValue().getDepartmentId());
        assertEquals("123", saved.getValue().getExternalMobile());
        verifyNoInteractions(users, projectManagers, serviceManagers);
        assertFalse(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.TASK_EXECUTION_CODES.contains("END_USER"));
        assertFalse(cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.MANAGEMENT_CODES.contains("END_USER"));
    }

    @Test void externalEditAppendsIntervalAndRemovalKeepsOldDetails() {
        var previous = member("END_USER"); previous.setUserId(null); previous.setMemberName("旧姓名");
        previous.setAuthorizationConfirmed(false);
        previous.setExternalMobile("旧电话"); previous.setDepartmentName("旧部门"); previous.setTitle("旧职务");
        when(memberMapper.selectById(10L)).thenReturn(previous);
        var values = new MemberValues(null, "END_USER", "原职责", "原备注", false, null,
                ExternalPerson.builder().name("新姓名").mobile("+86 (571) 8888-1234").department("新部门").title("新职务").build());
        service.mutate(new Command(100L, 0L, Action.UPDATE, 10L, values, "变更", "external-update"), actor());
        var closed = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).updateById(closed.capture());
        assertNotNull(closed.getValue().getEffectiveTo()); assertNull(closed.getValue().getMemberName());
        assertNull(closed.getValue().getAuthorizationConfirmed());
        assertEquals("旧姓名", previous.getMemberName()); assertEquals("旧电话", previous.getExternalMobile());
        assertEquals("旧部门", previous.getDepartmentName()); assertEquals("旧职务", previous.getTitle());
        assertNull(closed.getValue().getDepartmentName()); assertNull(closed.getValue().getTitle());
        var appended = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).insert(appended.capture());
        assertEquals("新部门", appended.getValue().getDepartmentName()); assertEquals("新职务", appended.getValue().getTitle());
        assertNull(appended.getValue().getUserId());
        assertEquals(Boolean.FALSE, appended.getValue().getAuthorizationConfirmed());
        verifyNoInteractions(users);
    }

    @Test void unchangedExternalDetailsDoNotWriteAndRemovalOnlyClosesHistory() {
        var previous = member("END_USER"); previous.setUserId(null); previous.setMemberName("姓名");
        previous.setAuthorizationConfirmed(false);
        previous.setCompanyName("单位"); previous.setExternalMobile("123"); previous.setExternalEmail("a@example.test");
        previous.setDepartmentName("部门"); previous.setTitle("职务");
        when(memberMapper.selectById(10L)).thenReturn(previous);
        var values = new MemberValues(null, "END_USER", "原职责", "原备注", false, null,
                ExternalPerson.builder().name("姓名").companyName("单位").mobile("123").email("a@example.test")
                        .department(" 部门 ").title(" 职务 ").build());
        assertFalse(service.mutate(new Command(100L, 0L, Action.UPDATE, 10L, values, "", "external-same"), actor()).changed());
        verify(memberMapper, never()).insert(any(ProjectMemberAssignmentDO.class));
        verify(memberMapper, never()).updateById(any(ProjectMemberAssignmentDO.class));
        assertTrue(service.mutate(new Command(100L, 0L, Action.REMOVE, 10L, null, "退出", "external-remove"), actor()).changed());
        verify(memberMapper).updateById(argThat((ProjectMemberAssignmentDO row) -> row.getAuthorizationConfirmed() == null
                && row.getEffectiveTo() != null));
        assertEquals("姓名", previous.getMemberName()); assertEquals("123", previous.getExternalMobile());
        assertEquals("部门", previous.getDepartmentName()); assertEquals("职务", previous.getTitle());
        verify(memberMapper, never()).deleteById(anyLong()); verifyNoInteractions(users);
    }

    @Test void externalRequiresNameAndRespectsStoredColumnBounds() {
        for (var person : List.of(ExternalPerson.builder().name(" ").build(),
                ExternalPerson.builder().name("x".repeat(129)).build(),
                ExternalPerson.builder().name("name").companyName("x".repeat(256)).build(),
                ExternalPerson.builder().name("name").mobile("x".repeat(65)).build(),
                ExternalPerson.builder().name("name").email("x".repeat(256)).build(),
                ExternalPerson.builder().name("name").department("x".repeat(65)).build(),
                ExternalPerson.builder().name("name").title("x".repeat(65)).build())) {
            var values = new MemberValues(null, "END_USER", null, null, false, null, person);
            assertThrows(ServiceException.class, () -> service.mutate(
                    new Command(100L, 0L, Action.ADD, null, values, "", "external-invalid"), actor()));
        }
        verify(memberMapper, never()).insert(any(ProjectMemberAssignmentDO.class)); verifyNoInteractions(users);
    }

    @Test void rejectsMalformedExternalPhoneAndEmailBeforeAnyWrite() {
        for (var person : List.of(
                ExternalPerson.builder().name("最终用户").mobile("任意内容").build(),
                ExternalPerson.builder().name("最终用户").mobile("+()-").build(),
                ExternalPerson.builder().name("最终用户").email("invalid-email").build())) {
            var values = new MemberValues(null, "END_USER", null, null, false, null, person);
            assertThrows(ServiceException.class, () -> service.mutate(
                    new Command(100L, 0L, Action.ADD, null, values, "", "invalid-format"), actor()));
        }
        verify(memberMapper, never()).insert(any(ProjectMemberAssignmentDO.class));
        verify(memberMapper, never()).updateById(any(ProjectMemberAssignmentDO.class));
    }

    @Test void externalContactAllowsEmptyValuesAndInternationalLandline() {
        for (var person : List.of(
                ExternalPerson.builder().name("最终用户").build(),
                ExternalPerson.builder().name("最终用户").mobile(" +86 (571) 8888-1234 ").email(" person@example.test ").build())) {
            var values = new MemberValues(null, "END_USER", null, null, false, null, person);
            assertTrue(service.mutate(new Command(100L, project.getVersion(), Action.ADD, null, values, "", "valid-format-" + project.getVersion()), actor()).changed());
        }
    }

    @Test void changingOnlyExternalDepartmentCreatesNewInterval() {
        assertExternalProfileChange(ExternalPerson.builder().name("姓名").department("新部门").title("原职务").build());
    }

    @Test void changingOnlyExternalTitleCreatesNewInterval() {
        assertExternalProfileChange(ExternalPerson.builder().name("姓名").department("原部门").title("新职务").build());
    }

    private void assertExternalProfileChange(ExternalPerson person) {
        var previous = member("END_USER"); previous.setUserId(null); previous.setMemberName("姓名");
        previous.setDepartmentName("原部门"); previous.setTitle("原职务");
        when(memberMapper.selectById(10L)).thenReturn(previous);
        var values = new MemberValues(null, "END_USER", "原职责", "原备注", false, null, person);
        assertTrue(service.mutate(new Command(100L, 0L, Action.UPDATE, 10L, values, "更新资料", "external-profile"), actor()).changed());
        var saved = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).insert(saved.capture());
        assertEquals(person.department(), saved.getValue().getDepartmentName());
        assertEquals(person.title(), saved.getValue().getTitle());
        assertEquals("原部门", previous.getDepartmentName()); assertEquals("原职务", previous.getTitle());
        assertEquals(Boolean.FALSE, saved.getValue().getAuthorizationConfirmed());
        verifyNoInteractions(users, projectManagers, serviceManagers);
    }

    @Test void rejectsAccountOnExternalRoleAndMissingAccountOnInternalRole() {
        var external = ExternalPerson.builder().name("姓名").build();
        for (var values : List.of(new MemberValues(40L, "END_USER", null, null, false, null, external),
                new MemberValues(null, "TEAM_MEMBER", null, null),
                new MemberValues(null, "END_USER", null, null, true, null, external),
                new MemberValues(null, "END_USER", null, null, false, new ServiceScope("L1", "PRIMARY", null, 1L, "D"), external),
                new MemberValues(40L, "TEAM_MEMBER", null, null, false, null, external))) {
            assertThrows(ServiceException.class, () -> service.mutate(
                    new Command(100L, 0L, Action.ADD, null, values, "加入", "invalid-external"), actor()));
        }
        verify(memberMapper, never()).insert(any(ProjectMemberAssignmentDO.class));
        verifyNoInteractions(users);
    }

    @Test void addsQualifiedTeamMemberWithoutCompanyScopeAndKeepsManagerFacts() {
        var result = service.mutate(command(Action.ADD, null, "TEAM_MEMBER", "现场实施", "人员备注", 0), actor());
        assertEquals(1, result.version()); assertTrue(result.changed());
        var member = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).insert(member.capture());
        assertEquals("普通人员", member.getValue().getMemberName());
        assertEquals(25L, member.getValue().getDepartmentId());
        assertEquals("其他部门", member.getValue().getDepartmentName());
        assertNull(member.getValue().getCompanyId());
        assertNull(member.getValue().getAssignmentType());
        assertEquals("人员备注", member.getValue().getRemark());
        assertEquals("本次调整", member.getValue().getChangeReason());
        verify(users).page(new ActiveUserSelectionApi.Query(1, 1, null, Set.of(40L), "PROJECT_MANAGER", null));
        assertEquals(55L, project.getManagerId()); assertEquals("ASSIGNED", project.getAssignmentStatus());
        assertEquals("S2", project.getCurrentStage());
        assertEquals(1, successFacts.businessEvents().size());
        var event = successFacts.businessEvents().getFirst();
        assertEquals("ProjectRuleReevaluationRequested", event.eventType());
        var context = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(event.eventPayload());
        assertEquals(100L, context.path("projectId").asLong());
        assertEquals(1L, context.path("tenantId").asLong());
        assertEquals(7L, context.path("actorId").asLong());
    }

    @Test void editingClosesOldIntervalAndAppendsInsteadOfRewritingIt() {
        var previous = member("TEAM_MEMBER");
        when(memberMapper.selectById(10L)).thenReturn(previous);
        var result = service.mutate(command(Action.UPDATE, 10L, "TEAM_MEMBER", "新职责", "新备注", 0), actor());
        assertEquals(90L, result.assignmentId());
        var close = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).updateById(close.capture());
        assertEquals(10L, close.getValue().getId()); assertNotNull(close.getValue().getEffectiveTo());
        assertEquals("本次调整", close.getValue().getEndReason());
        assertNull(close.getValue().getMemberName()); assertNull(close.getValue().getRemark());
        assertNull(close.getValue().getChangeReason());
        assertEquals("原职责", previous.getResponsibility()); assertEquals("原备注", previous.getRemark());
        assertEquals("原加入原因", previous.getChangeReason());
    }

    @Test void unavailableUserStopsBeforeClosingAnExistingMember() {
        when(memberMapper.selectById(10L)).thenReturn(member("TEAM_MEMBER"));
        when(users.page(any())).thenReturn(PageResult.empty());
        assertThrows(ServiceException.class, () -> service.mutate(command(Action.UPDATE, 10L, "TEAM_MEMBER", "新职责", "", 0), actor()));
        verify(memberMapper, never()).updateById(any(ProjectMemberAssignmentDO.class));
        verify(memberMapper, never()).insert(any(ProjectMemberAssignmentDO.class));
        verify(projectMapper, never()).incrementVersionIfMatch(anyLong(), anyLong());
    }

    @Test void duplicateUserAndRoleIsRejectedButDifferentRoleIsAllowed() {
        when(memberMapper.selectActiveMemberIdentityForUpdate(any())).thenReturn(List.of(member("TEAM_MEMBER")));
        var rejected = assertThrows(ServiceException.class, () -> service.mutate(command(Action.ADD, null, "TEAM_MEMBER", "", "", 0), actor()));
        assertEquals(PROJECT_TEAM_MEMBER_DUPLICATE.getCode(), rejected.getCode());
        when(memberMapper.selectActiveMemberIdentityForUpdate(any())).thenReturn(List.of());
        assertEquals("SALES_REPRESENTATIVE", service.mutate(command(Action.ADD, null, "SALES_REPRESENTATIVE", "", "", 0), actor()).memberRole());
        verify(memberMapper, times(1)).insert(any(ProjectMemberAssignmentDO.class));
    }

    @Test void teamMaintainerCannotAddOrRemoveManagerRoles() {
        assertEquals(PROJECT_AUTHORIZATION_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> service.mutate(command(Action.ADD, null, "PROJECT_MANAGER", "", "", 0), actor())).getCode());
        when(memberMapper.selectById(10L)).thenReturn(member("SERVICE_MANAGER_L1"));
        assertEquals(PROJECT_AUTHORIZATION_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> service.mutate(command(Action.REMOVE, 10L, null, null, null, 0), actor())).getCode());
        verify(memberMapper, never()).insert(any(ProjectMemberAssignmentDO.class));
        verify(memberMapper, never()).updateById(any(ProjectMemberAssignmentDO.class));
    }

    @Test void removalPreservesDataAndRepeatedRemovalDoesNotRewriteHistory() {
        var previous = member("TEAM_MEMBER"); when(memberMapper.selectById(10L)).thenReturn(previous);
        assertTrue(service.mutate(command(Action.REMOVE, 10L, null, null, null, 0), actor()).changed());
        assertEquals(1, successFacts.businessEvents().size());
        assertFalse(service.mutate(command(Action.REMOVE, 10L, null, null, null, 1), actor()).changed());
        assertTrue(successFacts.businessEvents().isEmpty());
        verify(memberMapper, times(1)).updateById(any(ProjectMemberAssignmentDO.class));
        verify(memberMapper, never()).deleteById(anyLong());
        assertEquals("原备注", previous.getRemark()); assertEquals("原加入原因", previous.getChangeReason());
    }

    @Test void removingUnlinkedProjectManagerClosesOnlyItsAssignmentAndRetainsHistory() {
        when(permissions.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        var previous = member("PROJECT_MANAGER");
        previous.setUserId(null); previous.setAuthorizationConfirmed(false);
        previous.setMemberName("历史项目经理");
        project.setManagerId(null); project.setAssignmentStatus("UNASSIGNED");
        when(memberMapper.selectById(10L)).thenReturn(previous);

        var result = service.mutate(command(Action.REMOVE, 10L, null, null, null, 0), actor());

        assertTrue(result.changed()); assertEquals(10L, result.assignmentId());
        assertNull(result.userId()); assertNotNull(result.effectiveTo()); assertEquals(1L, result.version());
        var closed = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).updateById(closed.capture());
        assertEquals(10L, closed.getValue().getId()); assertNotNull(closed.getValue().getEffectiveTo());
        assertEquals("本次调整", closed.getValue().getEndReason());
        assertNull(closed.getValue().getUserId()); assertNull(closed.getValue().getMemberName());
        assertEquals("历史项目经理", previous.getMemberName()); assertEquals("原加入原因", previous.getChangeReason());
        assertNull(project.getManagerId()); assertEquals("UNASSIGNED", project.getAssignmentStatus());
        assertEquals(1, successFacts.businessEvents().size());
        assertFalse(service.mutate(command(Action.REMOVE, 10L, null, null, null, 1), actor()).changed());
        verify(memberMapper, times(1)).updateById(any(ProjectMemberAssignmentDO.class));
        verifyNoInteractions(projectManagers, serviceManagers, users);
        verify(memberMapper, never()).deleteById(anyLong());
    }

    @Test void historicalEditAndCrossProjectAssignmentAreRejected() {
        var previous = member("TEAM_MEMBER"); previous.setEffectiveTo(LocalDateTime.now().minusHours(1));
        when(memberMapper.selectById(10L)).thenReturn(previous);
        assertThrows(ServiceException.class, () -> service.mutate(command(Action.UPDATE, 10L, "TEAM_MEMBER", "变更", "", 0), actor()));
        previous.setProjectId(101L);
        assertEquals(PROJECT_TEAM_MEMBER_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> service.mutate(command(Action.REMOVE, 10L, null, null, null, 0), actor())).getCode());
        verify(memberMapper, never()).updateById(any(ProjectMemberAssignmentDO.class));
    }

    @Test void removingBoundProjectManagerKeepsDedicatedPrimaryReplacementCommand() {
        when(permissions.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        var previous = member("PROJECT_MANAGER");
        when(memberMapper.selectById(10L)).thenReturn(previous);
        when(projectManagers.update(any(), any())).thenReturn(new ProjectManagerMemberResult(
                100L, 1L, 55L, "ASSIGNED", true, List.of()));

        service.mutate(new Command(100L, 0L, Action.REMOVE, 10L, null, "退出", "remove-linked", 55L), actor());

        var command = ArgumentCaptor.forClass(ProjectManagerMemberCommand.class);
        verify(projectManagers).update(command.capture(), any());
        assertEquals(Set.of(40L), command.getValue().removeUserIds());
        assertEquals(55L, command.getValue().primaryUserId());
        verify(projectMapper, never()).incrementVersionIfMatch(anyLong(), anyLong());
    }

    @Test void closedProjectAndStaleVersionCannotWrite() {
        project.setLifecycleStatus("CLOSED");
        assertThrows(ServiceException.class, () -> service.mutate(command(Action.ADD, null, "TEAM_MEMBER", "", "", 0), actor()));
        project.setLifecycleStatus("ACTIVE"); project.setVersion(2L);
        assertEquals(PROJECT_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.mutate(command(Action.ADD, null, "TEAM_MEMBER", "", "", 0), actor())).getCode());
        verifyNoInteractions(users); verify(memberMapper, never()).insert(any(ProjectMemberAssignmentDO.class));
    }

    @Test void unconfirmedHistoryCannotBeEditedAsAnOrdinaryCurrentMember() {
        var previous = member("TEAM_MEMBER"); previous.setAuthorizationConfirmed(false);
        when(memberMapper.selectById(10L)).thenReturn(previous);
        assertThrows(ServiceException.class, () -> service.mutate(command(Action.UPDATE, 10L, "TEAM_MEMBER", "变更", "", 0), actor()));
        verify(memberMapper, never()).updateById(any(ProjectMemberAssignmentDO.class));
        verify(memberMapper, never()).insert(any(ProjectMemberAssignmentDO.class));
    }

    @Test void replayReturnsSavedResultWithoutRevalidatingUserOrWriting() {
        var saved = new Result(100L, 1L, 90L, 40L, "TEAM_MEMBER", LocalDateTime.now(), null, true);
        doReturn(new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, saved))
                .when(commands).execute(any(), any(), any(), any(), any());
        assertSame(saved, service.mutate(command(Action.ADD, null, "TEAM_MEMBER", "", "", 0), actor()));
        assertNull(successFacts);
        verifyNoInteractions(users, memberMapper, projectMapper);
    }

    @Test void tenantAndPermissionFailuresStopBeforeIdempotencyAndDirectory() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> service.mutate(command(Action.ADD, null, "TEAM_MEMBER", "", "", 0), actor()));
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyPermissions(7L, OrdinaryProjectMemberService.WRITE_PERMISSION)).thenReturn(false);
        assertThrows(ServiceException.class, () -> service.candidates(100L, new CandidateFilter(1, 20, null, null, "TEAM_MEMBER", null), actor()));
        verifyNoInteractions(commands, users, authorization);
    }

    @Test void memberPageKeepsCurrentProjectAndTenantScope() {
        when(memberMapper.selectMemberPage(any())).thenReturn(PageResult.empty());
        service.page(100L, new Filter(2, 10, "HISTORY", "TEAM_MEMBER", " 张工 "), actor());
        verify(projects).getProject(eq(100L), any());
        var filter = ArgumentCaptor.forClass(ProjectMemberPageQuery.class);
        verify(memberMapper).selectMemberPage(filter.capture());
        assertEquals(1L, filter.getValue().getTenantId()); assertEquals(100L, filter.getValue().getProjectId());
        assertEquals("张工", filter.getValue().getKeyword()); assertEquals("HISTORY", filter.getValue().getState());
    }

    @Test void roleChangesSelectSystemQualificationWithoutAcceptingClientCompany() {
        when(permissions.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        service.candidates(100L, new CandidateFilter(1, 20, "张", null, "TEAM_MEMBER", null), actor());
        verify(users).page(new ActiveUserSelectionApi.Query(1, 20, "张", null, "PROJECT_MANAGER", null));
        service.candidates(100L, new CandidateFilter(1, 20, null, null, "SALES_REPRESENTATIVE", null), actor());
        verify(users).page(new ActiveUserSelectionApi.Query(1, 20, null, null, "SALES_REPRESENTATIVE", null));
        service.candidates(100L, new CandidateFilter(1, 20, null, null, "PROJECT_MANAGER", null), actor());
        verify(users).page(new ActiveUserSelectionApi.Query(1, 20, null, null, "PROJECT_MANAGER",
                new ActiveUserSelectionApi.Qualification(8L, null, null, "PROJECT_MANAGER")));
        service.candidates(100L, new CandidateFilter(1, 20, null, null, "SERVICE_MANAGER", null), actor());
        verify(users).page(new ActiveUserSelectionApi.Query(1, 20, null, null, "SERVICE_MANAGER", null));
    }

    @Test void projectManagerAdditionKeepsOriginalCommandAndDoesNotSilentlyReplacePrimary() {
        when(permissions.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        var added = member("PROJECT_MANAGER"); added.setId(77L);
        when(memberMapper.selectById(77L)).thenReturn(added);
        when(projectManagers.update(any(), any())).thenReturn(new ProjectManagerMemberResult(
                100L, 1L, 55L, "ASSIGNED", true, List.of(new ProjectManagerMemberResult.Member(77L, 40L, "人员", LocalDateTime.now()))));
        var result = service.mutate(command(Action.ADD, null, "PROJECT_MANAGER", "职责", "备注", 0), actor());
        assertEquals(77L, result.assignmentId()); assertEquals(1, result.version());
        var captured = ArgumentCaptor.forClass(ProjectManagerMemberCommand.class);
        verify(projectManagers).update(captured.capture(), any());
        assertEquals(Set.of(40L), captured.getValue().addUserIds());
        assertTrue(captured.getValue().removeUserIds().isEmpty());
        assertNull(captured.getValue().primaryUserId());
        verify(users).page(new ActiveUserSelectionApi.Query(1, 1, null, Set.of(40L), "PROJECT_MANAGER",
                new ActiveUserSelectionApi.Qualification(8L, null, null, "PROJECT_MANAGER")));
    }

    @Test void managerDetailsAppendHistoryWithoutReassigningOrChangingIdentity() {
        when(permissions.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        var old = member("PROJECT_MANAGER"); old.setCompanyId(8L); old.setAssignmentType("COLLABORATOR");
        when(memberMapper.selectById(10L)).thenReturn(old);
        var result = service.mutate(command(Action.UPDATE, 10L, "PROJECT_MANAGER", "新职责", "新备注", 0), actor());
        assertEquals(90L, result.assignmentId());
        assertEquals("原备注", old.getRemark()); assertNull(old.getEffectiveTo());
        var captured = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).insert(captured.capture());
        assertEquals("PROJECT_MANAGER", captured.getValue().getMemberRole());
        assertEquals(8L, captured.getValue().getCompanyId());
        assertEquals("新备注", captured.getValue().getRemark());
        verifyNoInteractions(projectManagers, serviceManagers);
    }

    @Test void removedLegacyRoleOptionsCannotCreateNewAssignments() {
        assertEquals(PROJECT_MEMBER_ROLE_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.mutate(command(Action.ADD, null, "ENGINEER", "", "", 0), actor())).getCode());
        assertEquals(PROJECT_MEMBER_ROLE_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.mutate(command(Action.ADD, null, "OUTSOURCED_ENGINEER", "", "", 0), actor())).getCode());
        verifyNoInteractions(users, projectManagers, serviceManagers);
    }

    @Test void serviceManagerCanBeAddedWithoutOfficeSiteOrLevel() {
        when(permissions.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        when(projectMapper.updateAssignmentStatusIfVersion(any())).thenReturn(1);
        var result = service.mutate(command(Action.ADD, null, "SERVICE_MANAGER", "", "备注", 0), actor());
        assertTrue(result.changed());
        var saved = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper).insert(saved.capture());
        assertEquals("SERVICE_MANAGER", saved.getValue().getMemberRole());
        assertEquals("PRIMARY", saved.getValue().getAssignmentType());
        assertNull(saved.getValue().getDepartmentId()); assertNull(saved.getValue().getSiteId());
        verify(users).page(new ActiveUserSelectionApi.Query(1, 1, null, Set.of(40L), "SERVICE_MANAGER", null));
        verifyNoInteractions(projectManagers, serviceManagers);
    }

    @Test void switchingServicePrimaryAppendsIntervalsAndKeepsPreviousPersonAsMember() {
        when(permissions.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        when(projectMapper.updateAssignmentStatusIfVersion(any())).thenReturn(1);
        var old = member("SERVICE_MANAGER_L2"); old.setUserId(41L); old.setAssignmentType("PRIMARY");
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(old));
        var values = new MemberValues(40L, "SERVICE_MANAGER", "", "", true, null);
        service.mutate(new Command(100L, 0L, Action.ADD, null, values, "切换主责", "service-primary"), actor());
        var saved = ArgumentCaptor.forClass(ProjectMemberAssignmentDO.class);
        verify(memberMapper, org.mockito.Mockito.times(2)).insert(saved.capture());
        assertEquals(41L, saved.getAllValues().get(0).getUserId());
        assertEquals("COLLABORATOR", saved.getAllValues().get(0).getAssignmentType());
        assertEquals("SERVICE_MANAGER", saved.getAllValues().get(0).getMemberRole());
        assertEquals("PRIMARY", saved.getAllValues().get(1).getAssignmentType());
        assertNull(old.getEffectiveTo()); assertEquals("PRIMARY", old.getAssignmentType());
    }

    private ProjectMemberAssignmentDO member(String role) {
        var member = new ProjectMemberAssignmentDO(); member.setId(10L); member.setTenantId(1L);
        member.setProjectId(100L); member.setUserId(40L); member.setMemberRole(role); member.setStatus("ACTIVE");
        member.setEffectiveFrom(LocalDateTime.now().minusDays(1)); member.setVersion(0);
        member.setResponsibility("原职责"); member.setRemark("原备注"); member.setChangeReason("原加入原因");
        return member;
    }
    private Command command(Action action, Long id, String role, String duty, String remark, long version) {
        return new Command(100L, version, action, id, action == Action.REMOVE ? null : new MemberValues(40L, role, duty, remark),
                "本次调整", "ordinary-member-test");
    }
    private Actor actor() { return new Actor(1L, 7L, "member-test"); }
}
