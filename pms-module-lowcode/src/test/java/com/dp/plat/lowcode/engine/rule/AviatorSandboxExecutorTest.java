package com.dp.plat.lowcode.engine.rule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aviator 沙箱执行器测试。
 *
 * <p>验证正常表达式可执行，危险操作（系统函数 {@code sysdate} / 反射调用 {@code Runtime.getRuntime()}）
 * 被沙箱拒绝。</p>
 */
@DisplayName("Aviator 沙箱执行器测试")
class AviatorSandboxExecutorTest {

    private AviatorSandboxExecutor sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new AviatorSandboxExecutor();
    }

    @Test
    @DisplayName("正常算术表达式可执行")
    void shouldEvaluateArithmeticExpression() {
        Object result = sandbox.execute("1 + 2", new HashMap<>());
        assertThat(result).isEqualTo(3L);
    }

    @Test
    @DisplayName("正常变量绑定表达式可执行")
    void shouldEvaluateVariableExpression() {
        Map<String, Object> env = new HashMap<>();
        env.put("a", 10);
        env.put("b", 20);
        Object result = sandbox.execute("a + b", env);
        assertThat(result).isEqualTo(30L);
    }

    @Test
    @DisplayName("正常逻辑表达式可执行")
    void shouldEvaluateBooleanExpression() {
        Map<String, Object> env = new HashMap<>();
        env.put("x", 15);
        Object result = sandbox.execute("x > 10 ? \"big\" : \"small\"", env);
        assertThat(result).isEqualTo("big");
    }

    @Test
    @DisplayName("sysdate() 系统函数被禁用")
    void shouldRejectSysdateFunction() {
        assertThatThrownBy(() -> sandbox.execute("sysdate()", new HashMap<>()))
                .isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("now() 系统函数被禁用")
    void shouldRejectNowFunction() {
        assertThatThrownBy(() -> sandbox.execute("now()", new HashMap<>()))
                .isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("Runtime.getRuntime() 反射调用被禁用（NewInstance 禁用）")
    void shouldRejectRuntimeReflection() {
        // 通过 NewInstance 禁用阻断 Java 类加载与静态方法调用，防止 RCE
        assertThatThrownBy(() -> sandbox.execute("Runtime.getRuntime()", new HashMap<>()))
                .isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("new 关键字构造对象被禁用（NewInstance 禁用）")
    void shouldRejectNewInstance() {
        assertThatThrownBy(() -> sandbox.execute(
                "new java.lang.ProcessBuilder(\"ls\")", new HashMap<>()))
                .isInstanceOf(Throwable.class);
    }

    @Test
    @DisplayName("get_sys_prop 系统函数被禁用")
    void shouldRejectGetSysProp() {
        assertThatThrownBy(() -> sandbox.execute("get_sys_prop(\"os.name\")", new HashMap<>()))
                .isInstanceOf(Throwable.class);
    }
}
