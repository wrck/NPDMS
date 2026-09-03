package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.configuration.CutoverConfigurationRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.CutoverConfigurationRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CutoverNavigationDecisionQueryServiceTest {

    @Test
    void readsTheTaskFrozenRevisionAndDefaultsHistoricalNull() {
        CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        CutoverConfigurationRevisionMapper revisions = mock(CutoverConfigurationRevisionMapper.class);
        CutoverTaskDO task = new CutoverTaskDO();
        task.setId(31L);
        task.setTenantId(1L);
        task.setConfigurationRevisionId(41L);
        task.setConfigurationCode("CUT-CONFIG");
        task.setConfigurationRevisionNo(2);
        CutoverConfigurationRevisionDO revision = new CutoverConfigurationRevisionDO();
        revision.setId(41L);
        revision.setTenantId(1L);
        revision.setConfigurationCode("CUT-CONFIG");
        revision.setRevisionNo(2);
        revision.setStatusCode("DISABLED");
        when(tasks.selectById(31L)).thenReturn(task);
        when(revisions.selectById(41L)).thenReturn(revision);
        CutoverNavigationDecisionQueryService service = new CutoverNavigationDecisionQueryService(
                tasks, revisions, new CutoverNavigationDecisionPolicy());

        var decision = service.decide(1L, 31L);

        assertEquals("POST_SUBMIT", decision.ruleKey());
        assertEquals(41L, decision.configurationRevisionId());
        assertEquals("CURRENT_STAGE_WORKBENCH", decision.target());
    }
}
