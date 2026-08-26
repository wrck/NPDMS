package cn.iocoder.yudao.module.pms.engineering.constructionplan;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * F-SOL-001 客户延期依据的真实 MySQL、Flowable、PROJ、PLT、SOL 聚合验收入口。
 *
 * <p>沿用已经过独立复审的 BPM 实库夹具，统一执行提交、三终态、材料重验及并发边界。</p>
 */
@EnabledIfSystemProperty(named = "skipITs", matches = "false")
class DurationChangeCustomerEvidenceEndToEndTest extends DurationChangeBpmMySqlIntegrationTest {
}
