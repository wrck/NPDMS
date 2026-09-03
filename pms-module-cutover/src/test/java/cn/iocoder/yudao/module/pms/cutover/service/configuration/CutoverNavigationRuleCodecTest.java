package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationSaveReqVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CutoverNavigationRuleCodecTest {

    @Test
    void roundTripsBothTargetsAndDefaultsHistoricalNull() {
        assertEquals("CURRENT_STAGE_WORKBENCH", roundTrip("CURRENT_STAGE_WORKBENCH"));
        assertEquals("TASK_OVERVIEW", roundTrip("TASK_OVERVIEW"));
        assertNull(CutoverNavigationRuleCodec.decode(null));
        assertEquals(CutoverNavigationRuleCodec.NavigationTarget.CURRENT_STAGE_WORKBENCH,
                CutoverNavigationRuleCodec.targetOrDefault(null));
    }

    private static String roundTrip(String target) {
        CutoverConfigurationSaveReqVO.NavigationRuleVO rule = new CutoverConfigurationSaveReqVO.NavigationRuleVO();
        rule.setTarget(target);
        return CutoverNavigationRuleCodec.decode(CutoverNavigationRuleCodec.encode(rule)).getTarget();
    }
}
