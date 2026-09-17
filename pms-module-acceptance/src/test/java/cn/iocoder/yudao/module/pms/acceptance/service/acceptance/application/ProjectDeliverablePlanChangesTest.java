package cn.iocoder.yudao.module.pms.acceptance.service.acceptance.application;

import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AccProjectDeliverableMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDeliverablePlanChangesTest {
    final AccProjectDeliverableMapper mapper=mock(AccProjectDeliverableMapper.class);
    final ProjectDeliverableInitializationApplicationServiceImpl service=new ProjectDeliverableInitializationApplicationServiceImpl();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); ReflectionTestUtils.setField(service,"mapper",mapper);
        when(mapper.selectByIdForUpdate(any())).thenAnswer(call -> current(call.<cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery>getArgument(0).deliverableId()));
        when(mapper.stagePlanCodeForRename(any())).thenReturn(1); when(mapper.updatePlanDefinition(any())).thenReturn(1);
        when(mapper.retireUnhandledForPlan(any())).thenReturn(1); when(mapper.insert(any(AccProjectDeliverableDO.class))).thenReturn(1);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void inspectionLocksOwnerRootsBeforeReadingRetirementEligibilityWithoutWrites() {
        when(mapper.selectPlanDefinitionsForUpdate(any())).thenReturn(List.of(current(10L)));
        when(mapper.selectRetirablePlanDefinitionIds(any())).thenReturn(List.of(10L));
        var state=service.inspectPlanDefinitions(9L);
        assertEquals(10L,state.definitions().getFirst().id()); assertEquals(java.util.Set.of(10L),state.retirableIds());
        var order=inOrder(mapper);
        order.verify(mapper).selectPlanDefinitionsForUpdate(argThat(query -> query.tenantId()==1L && query.projectId()==9L));
        order.verify(mapper).selectRetirablePlanDefinitionIds(any());
        verify(mapper,never()).updatePlanDefinition(any()); verify(mapper,never()).retireUnhandledForPlan(any());
    }
    @Test void swapsCodesInTwoPhasesAndLeavesSourceAndArchiveStateToTheOwner() {
        service.applyPlanChanges(command(new DeliverablePlanChange(20L,3,definition("D10")),new DeliverablePlanChange(10L,3,definition("D20"))));
        var order=inOrder(mapper);
        order.verify(mapper).selectByIdForUpdate(argThat(query -> query.deliverableId()==10L));
        order.verify(mapper).selectByIdForUpdate(argThat(query -> query.deliverableId()==20L));
        order.verify(mapper).stagePlanCodeForRename(argThat(query -> query.id()==10L));
        order.verify(mapper).stagePlanCodeForRename(argThat(query -> query.id()==20L));
        order.verify(mapper).updatePlanDefinition(argThat(query -> query.id()==10L && query.definition().getDeliverableCode().equals("D20")));
        order.verify(mapper).updatePlanDefinition(argThat(query -> query.id()==20L && query.definition().getDeliverableCode().equals("D10")));
        verify(mapper,never()).clearCurrentSource(any()); verify(mapper,never()).retireUnhandledForPlan(any());
    }
    @Test void removesUnhandledInstanceBeforeAddingIndependentReplacementWithSameCode() {
        service.applyPlanChanges(command(new DeliverablePlanChange(10L,3,null),new DeliverablePlanChange(null,null,definition("D10"))));
        var order=inOrder(mapper); order.verify(mapper).retireUnhandledForPlan(argThat(query -> query.id()==10L));
        order.verify(mapper).insert(argThat((AccProjectDeliverableDO row) -> row.getId()==null && row.getProjectId()==9L
                && row.getTenantId()==1L && row.getStatus().equals("PENDING") && row.getCurrentSourceVersionId()==null));
    }
    @Test void staleOrForeignPreviewCannotWriteAnyDefinition() {
        assertThrows(IllegalStateException.class,()->service.applyPlanChanges(command(new DeliverablePlanChange(10L,2,definition("D10")))));
        var foreign=current(10L); foreign.setProjectId(99L); doReturn(foreign).when(mapper).selectByIdForUpdate(any());
        assertThrows(IllegalStateException.class,()->service.applyPlanChanges(command(new DeliverablePlanChange(10L,3,null))));
        foreign.setProjectId(9L); foreign.setTenantId(2L);
        assertThrows(IllegalStateException.class,()->service.applyPlanChanges(command(new DeliverablePlanChange(10L,3,null))));
        verify(mapper,never()).updatePlanDefinition(any()); verify(mapper,never()).stagePlanCodeForRename(any());
        verify(mapper,never()).retireUnhandledForPlan(any());
    }
    @Test void rejectsDuplicateIdentityOrTargetCodeBeforeTakingLocks() {
        assertThrows(IllegalArgumentException.class,()->service.applyPlanChanges(command(new DeliverablePlanChange(10L,3,null),new DeliverablePlanChange(10L,3,null))));
        assertThrows(IllegalArgumentException.class,()->service.applyPlanChanges(command(new DeliverablePlanChange(10L,3,definition("SAME")),new DeliverablePlanChange(null,null,definition("SAME")))));
        verify(mapper,never()).selectByIdForUpdate(any());
    }
    @Test void refusedRetirementDoesNotContinueWithOtherChanges() {
        when(mapper.retireUnhandledForPlan(any())).thenReturn(0);
        var failure=assertThrows(IllegalStateException.class,()->service.applyPlanChanges(command(new DeliverablePlanChange(10L,3,null),new DeliverablePlanChange(null,null,definition("D10")))));
        assertEquals("DELIVERABLE_PLAN_HANDLING_HISTORY_PROTECTED",failure.getMessage());
        verify(mapper,never()).insert(any(AccProjectDeliverableDO.class)); verify(mapper,never()).updatePlanDefinition(any());
    }
    @Test void emptyChangeSetHasNoPersistenceSideEffects() {
        service.applyPlanChanges(command()); verifyNoInteractions(mapper);
    }
    ApplyDeliverablePlanChanges command(DeliverablePlanChange... changes) { return new ApplyDeliverablePlanChanges(9L,7L,List.of(changes)); }
    DeliverableDefinition definition(String code) { return new DeliverableDefinition(code,"现场工勘交付件","PREP","SURVEY",true,null); }
    AccProjectDeliverableDO current(Long id) {
        var row=new AccProjectDeliverableDO(); row.setId(id); row.setTenantId(1L); row.setProjectId(9L); row.setVersion(3);
        row.setDeliverableCode("D"+id); row.setStatus("SUBMITTED"); row.setCurrentSourceVersionId(88L); row.setArchiveStatus("VALID"); return row;
    }
}
