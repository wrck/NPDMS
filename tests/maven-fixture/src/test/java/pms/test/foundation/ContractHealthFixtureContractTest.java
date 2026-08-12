package pms.test.foundation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-CP-009 测试分类骨架健康检查 fixture。
 * <p>
 * 本类被 {@code pms-test-contract} profile 的 surefire include
 * {@code **&#47;*ContractTest.java} 选中；在 {@code pms-test-unit} profile 下
 * 虽然匹配 {@code **&#47;*Test.java}，但被其 exclude {@code **&#47;*ContractTest.java}
 * 排除。测试名必须以 {@code ContractTest} 结尾。
 * <p>
 * 健康检查脚本 {@code tests/e2e/verify-test-foundation.ps1} 期望在该 profile 下
 * 仅产出 {@code TEST-pms.test.foundation.ContractHealthFixtureContractTest.xml}
 * 一个 surefire 报告，且 {@code tests} 计数为 1，因此本类只能保留一个 {@code @Test} 方法。
 */
class ContractHealthFixtureContractTest {

    @Test
    void contractHealthFixturePasses() {
        assertTrue(true, "contract health fixture");
    }
}
