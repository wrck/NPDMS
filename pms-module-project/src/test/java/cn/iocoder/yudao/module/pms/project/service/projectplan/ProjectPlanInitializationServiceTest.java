package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanInitializationServiceTest {
    @Test void freezesProjectOwnedVersionAndCreatesIndependentFirstExecutionRounds() {
        var plans = mock(ProjectPlanVersionMapper.class); var rounds = mock(ProjectNodeExecutionMapper.class);
        var templates = mock(ProjectTemplateService.class);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setLifecycleTemplateId(100L);
        project.setLifecycleTemplateRevisionId(102L); project.setLifecycleTemplateRevisionNo(2);
        var source = new ProjectTemplateRevisionDO(); source.setId(102L); source.setTenantId(7L); source.setTemplateId(100L);
        source.setRevisionNo(2); source.setStatus("PUBLISHED"); source.setDesignerDocument("{\"schemaVersion\":2,\"rules\":[]}");
        when(templates.getRevisionById(102L)).thenReturn(source);
        when(plans.insert(any(ProjectPlanVersionDO.class))).thenAnswer(call -> {call.getArgument(0,ProjectPlanVersionDO.class).setId(51L); return 1;});
        when(plans.attachInitialPlan(any())).thenReturn(1); when(rounds.insert(any(ProjectNodeExecutionDO.class))).thenReturn(1);
        var snapshot = new TemplateExecutionSnapshot(); var node = new TemplateExecutionSnapshot.StageContract(); node.setNodeKey("stage:one"); snapshot.setStages(List.of(node));
        var contract = new ProjectStageExecutionContractDO(); contract.setId(31L); contract.setStageId(11L); contract.setSourceNodeKey("stage:one");
        var service = new ProjectPlanInitializationService(plans, rounds, templates);
        service.initialize(project, snapshot, List.of(contract), List.of());
        var version = ArgumentCaptor.forClass(ProjectPlanVersionDO.class); verify(plans).insert(version.capture());
        assertEquals(1, version.getValue().getRevisionNo()); assertEquals(102L, version.getValue().getSourceTemplateRevisionId());
        assertEquals(51L, project.getActivePlanVersionId());
        snapshot.getStages().getFirst().setNodeKey("changed");
        assertEquals("stage:one", JsonUtils.parseTree(version.getValue().getExecutionSnapshot()).path("stages").get(0).path("nodeKey").asText());
        var execution = ArgumentCaptor.forClass(ProjectNodeExecutionDO.class); verify(rounds).insert(execution.capture());
        assertEquals(1, execution.getValue().getRoundNo()); assertEquals("PENDING", execution.getValue().getStatus());
        assertNull(execution.getValue().getSubmittedAt()); assertEquals(31L, execution.getValue().getContractId());
        assertThrows(IllegalArgumentException.class, () -> service.initialize(project, snapshot, List.of(contract), List.of()));
        verify(templates).getRevisionById(102L);
    }
}
