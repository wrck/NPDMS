package cn.iocoder.yudao.module.pms.project.service.stagebusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageApprovalServiceTest {
    final ProjectNodeExecutionMapper rounds = mock(ProjectNodeExecutionMapper.class);
    final ProjectNodeApprovalApi approvals = mock(ProjectNodeApprovalApi.class);
    final ProjectStageApprovalService service = new ProjectStageApprovalService(rounds, approvals);
    final ProjectStageExecutionContext execution = new ProjectStageExecutionContext(9L,1,11L,1,41L,1,21L,31L,2,3,true);
    final BindingContract binding = new BindingContract();
    ProjectNodeExecutionDO round;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        binding.setType("APPROVAL"); binding.setApprovalDefinitionKey("review");
        binding.setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"review:1\"}"));
        round = new ProjectNodeExecutionDO(); round.setId(31L); round.setTenantId(7L); round.setProjectId(9L);
        round.setNodeKind("STAGE"); round.setNodeInstanceId(11L); round.setContractId(41L); round.setPlanVersionId(21L);
        round.setVersion(2); round.setRoundNo(3); round.setCurrentMarker(1);
        round.setStartedAt(LocalDateTime.of(2026,9,15,9,0));
        when(rounds.selectById(31L)).thenAnswer(call -> round);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    View view() { return service.view(7L, execution, binding); }

    @Test void usesFrozenDefinitionAndActualStageRoundTimeInsteadOfActivationOrCurrentTime() {
        var fact = new Fact(Outcome.SATISFIED,"APPROVED","pi","review:1",null);
        when(approvals.inspect(any())).thenReturn(fact);
        assertEquals(new View("review","review:1",31L,fact), view());
        verify(approvals).inspect(new Scope(NodeKind.STAGE,7L,9L,11L,31L,41L,"review","review:1",round.getStartedAt()));
    }

    @Test void unstartedRoundDoesNotReusePreviousApproval() {
        round.setStartedAt(null);
        assertEquals("NOT_STARTED", view().current().status());
        assertEquals(Outcome.NOT_SATISFIED, view().current().outcome());
        verifyNoInteractions(approvals);
    }

    @Test void missingFrozenDefinitionCannotResolveLatest() {
        binding.setParameters(JsonUtils.parseTree("{}"));
        assertThrows(IllegalArgumentException.class, this::view);
        verifyNoInteractions(rounds, approvals);
    }

    @Test void wrongTenantProjectNodeContractPlanVersionOrRoundDoesNotReadBpm() {
        List<Consumer<ProjectNodeExecutionDO>> changes = List.of(
                r -> r.setTenantId(8L), r -> r.setProjectId(10L), r -> r.setNodeKind("TASK"),
                r -> r.setNodeInstanceId(12L), r -> r.setContractId(42L), r -> r.setPlanVersionId(22L),
                r -> r.setVersion(3), r -> r.setRoundNo(4), r -> r.setCurrentMarker(null));
        for (var change : changes) {
            setup(); change.accept(round);
            assertEquals(Outcome.UNKNOWN, view().current().outcome());
        }
        verifyNoInteractions(approvals);
    }

    @Test void tenantContextMismatchAndMissingRoundAreUnknown() {
        TenantContextHolder.setTenantId(8L);
        assertEquals(Outcome.UNKNOWN, view().current().outcome());
        verifyNoInteractions(rounds, approvals);
        TenantContextHolder.setTenantId(7L); round = null;
        assertEquals(Outcome.UNKNOWN, view().current().outcome());
        verifyNoInteractions(approvals);
    }

    @Test void ownerUnavailableNeverBecomesNotSatisfiedWithAnInstanceId() {
        assertEquals(Outcome.UNKNOWN, view().current().outcome());
        when(approvals.inspect(any())).thenThrow(new IllegalStateException("private-owner-detail"));
        var fact = view().current();
        assertEquals(Outcome.UNKNOWN, fact.outcome()); assertNull(fact.processInstanceId());
        assertEquals("STAGE_APPROVAL_FACT_UNAVAILABLE", fact.reason());
    }
}
