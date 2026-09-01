package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFactQuery;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.stagegate.ProjectLocalStageGateFactMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectLocalStageGateFactProviderTest {

    private ProjectLocalStageGateFactMapper mapper;
    private ProjectLocalStageGateFactProvider provider;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        mapper = mock(ProjectLocalStageGateFactMapper.class);
        provider = new ProjectLocalStageGateFactProvider(mapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void satisfiesTaskMilestoneAndStateFromOwnerRows() {
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO().setId(11L).setStatus("DONE").setVersion(2);
        ProjectMilestoneInstanceDO milestone = new ProjectMilestoneInstanceDO()
                .setId(12L).setStatus("ACHIEVED").setVersion(3);
        ProjectStageInstanceDO stage = new ProjectStageInstanceDO().setId(13L).setStatus("DONE").setVersion(4);
        when(mapper.selectTaskForUpdate(any())).thenReturn(task);
        when(mapper.selectMilestoneForUpdate(any())).thenReturn(milestone);
        when(mapper.selectStageForUpdate(any())).thenReturn(stage);

        assertEquals(ProjectStageGateOutcome.SATISFIED, provider.lockAndRevalidate(query("TASK", "T-01")).outcome());
        assertEquals(ProjectStageGateOutcome.SATISFIED,
                provider.lockAndRevalidate(query("MILESTONE", "M-01")).outcome());
        assertEquals(ProjectStageGateOutcome.SATISFIED,
                provider.lockAndRevalidate(query("STATE", "S0_COMPLETED")).outcome());
    }

    private static ProjectStageGateFactQuery query(String type, String code) {
        return new ProjectStageGateFactQuery(7L, 9L, "S0", 21L, "G-01", 0,
                22L, 0, type, code);
    }
}
