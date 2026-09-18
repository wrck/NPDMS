package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterLegacyConvert;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 项目主档新旧粗化状态契约映射测试（AI-MIG-000 口径A / V260 契约）。
 * <p>
 * 前向口径（V260）：旧 Integer 0/1/2/3 -> 新 String S0/S1/S6/S6（2,3 并入 S6）；
 * 逆向终态还原：S0->0 / S1->1 / S6->3（旧 2 的显示伪影已在转换器类注释记录）。
 */
class ProjectMasterLegacyConvertTest {

    @Test
    void forwardMappingFollowsV260CoarseContract() {
        assertEquals("S0", ProjectMasterLegacyConvert.toMasterStatus(0));
        assertEquals("S1", ProjectMasterLegacyConvert.toMasterStatus(1));
        assertEquals("S6", ProjectMasterLegacyConvert.toMasterStatus(2));
        assertEquals("S6", ProjectMasterLegacyConvert.toMasterStatus(3));
        assertNull(ProjectMasterLegacyConvert.toMasterStatus(null));
        assertNull(ProjectMasterLegacyConvert.toMasterStatus(9));
    }

    @Test
    void reverseMappingRestoresTerminalLegacyValues() {
        assertEquals(0, ProjectMasterLegacyConvert.toLegacyStatus("S0"));
        assertEquals(1, ProjectMasterLegacyConvert.toLegacyStatus("S1"));
        assertEquals(3, ProjectMasterLegacyConvert.toLegacyStatus("S6"));
        assertNull(ProjectMasterLegacyConvert.toLegacyStatus(null));
        assertNull(ProjectMasterLegacyConvert.toLegacyStatus("S2"));
    }

    @Test
    void governanceWriteEndpointsMapToRealMasterStageCodes() {
        // 治理写端点：ROLLBACK -> S0（待指派）/ DIRECT_CLOSE -> S6（已关闭）
        assertEquals("S0", ProjectMasterLegacyConvert.toMasterStatus(
                ProjectMasterLegacyConvert.LEGACY_STATUS_PENDING_ASSIGN));
        assertEquals("S6", ProjectMasterLegacyConvert.toMasterStatus(
                ProjectMasterLegacyConvert.LEGACY_STATUS_CLOSED));
        // 动作审计逆向：S6 -> 3（旧已关闭终态）
        assertEquals(ProjectMasterLegacyConvert.LEGACY_STATUS_CLOSED,
                ProjectMasterLegacyConvert.toLegacyStatus(
                        ProjectMasterLegacyConvert.MASTER_STATUS_CLOSED));
    }

    @Test
    void roundTripThroughCoarseContractIsStableOnTerminalValues() {
        // 终态值往返稳定：0->S0->0 / 1->S1->1 / 3->S6->3（2 的粗化并集不保证往返）
        for (Integer legacy : new Integer[]{0, 1, 3}) {
            assertEquals(legacy, ProjectMasterLegacyConvert.toLegacyStatus(
                    ProjectMasterLegacyConvert.toMasterStatus(legacy)));
        }
        for (String master : new String[]{"S0", "S1", "S6"}) {
            assertEquals(master, ProjectMasterLegacyConvert.toMasterStatus(
                    ProjectMasterLegacyConvert.toLegacyStatus(master)));
        }
    }
}
