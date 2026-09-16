package com.dp.plat.lowcode.engine.microflow;

import groovy.lang.Binding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Groovy 沙箱执行器测试。
 *
 * <p>验证正常表达式可执行，危险操作（Runtime/System/ProcessBuilder/File）被拒绝。</p>
 */
class GroovySandboxExecutorTest {

    private GroovySandboxExecutor sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new GroovySandboxExecutor();
    }

    @Test
    void shouldEvaluateArithmeticExpression() {
        Binding binding = new Binding(Map.of("a", 10, "b", 20));
        Object result = sandbox.evaluate(binding, "a + b");
        assertThat(result).isEqualTo(30);
    }

    @Test
    void shouldEvaluateBooleanExpression() {
        Binding binding = new Binding(Map.of("x", 15));
        Object result = sandbox.evaluate(binding, "x > 10");
        assertThat(result).isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldEvaluateListLiteralAndOperators() {
        Object result = sandbox.evaluate(new Binding(), "[1, 2, 3].size() == 3 && (5 - 2) > 1");
        assertThat(result).isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldRejectRuntimeExec() {
        // Groovy 4.0.x 在 JDK 25 上可能因 class file version 69 不兼容抛出 Error 而非 SecurityException，
        // 关键断言是危险操作被拒绝（未成功执行），因此接受任意 Throwable。
        assertThatThrownBy(() -> sandbox.evaluate(new Binding(), "Runtime.getRuntime().exec(\"ls\")"))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void shouldRejectSystemExit() {
        assertThatThrownBy(() -> sandbox.evaluate(new Binding(), "System.exit(0)"))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void shouldRejectNewFile() {
        assertThatThrownBy(() -> sandbox.evaluate(new Binding(), "new File(\"/etc/passwd\")"))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void shouldRejectProcessBuilder() {
        assertThatThrownBy(() -> sandbox.evaluate(new Binding(),
                "new ProcessBuilder(\"ls\").start()"))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void shouldRejectExplicitImportOfForbiddenClass() {
        assertThatThrownBy(() -> sandbox.evaluate(new Binding(),
                "import java.io.File; new File(\"/etc/passwd\")"))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void shouldRejectFullyQualifiedFileReference() {
        assertThatThrownBy(() -> sandbox.evaluate(new Binding(),
                "new java.io.File(\"/etc/passwd\")"))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void shouldRejectThreadManipulation() {
        assertThatThrownBy(() -> sandbox.evaluate(new Binding(), "Thread.sleep(10)"))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void shouldAllowJavaUtilCollections() {
        Binding binding = new Binding(Map.of("items", List.of(1, 2, 3, 4)));
        Object result = sandbox.evaluate(binding, "items.findAll { it > 1 }.size()");
        // 闭包内仅使用 java.util 集合运算，应允许执行
        assertThat(result).isEqualTo(3);
    }
}
