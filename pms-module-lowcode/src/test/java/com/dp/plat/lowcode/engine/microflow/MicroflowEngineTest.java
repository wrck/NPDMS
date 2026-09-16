package com.dp.plat.lowcode.engine.microflow;

import com.dp.plat.lowcode.mapper.LowCodeMicroflowExecutionLogMapper;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MicroflowEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LowCodeMicroflowExecutionLogMapper executionLogMapper = mock(LowCodeMicroflowExecutionLogMapper.class);

    private GroovySandboxExecutor sandbox;
    private MicroflowEngine engine;

    @BeforeEach
    void setUp() {
        sandbox = new GroovySandboxExecutor();
        engine = new MicroflowEngine(objectMapper, List.of(
                new StartEndExecutor(),
                new AssignExecutor(sandbox),
                new ConditionExecutor(sandbox),
                new ReturnExecutor(sandbox),
                new ThrowExceptionExecutor()), executionLogMapper);
    }

    private MicroflowContext run(String definition) {
        return engine.execute(1L, "test", definition, Map.of());
    }

    @Test
    void shouldExecuteSimpleAssignAndReturn() {
        String definition = "{\"nodes\":[" +
                "{\"id\":\"s\",\"type\":\"START\"}," +
                "{\"id\":\"a\",\"type\":\"ASSIGN\",\"config\":{\"target\":\"x\",\"expression\":\"1 + 2\"}}," +
                "{\"id\":\"r\",\"type\":\"RETURN\",\"config\":{\"expression\":\"x * 10\"}}," +
                "{\"id\":\"e\",\"type\":\"END\"}]," +
                "\"edges\":[" +
                "{\"source\":\"s\",\"target\":\"a\"}," +
                "{\"source\":\"a\",\"target\":\"r\"}," +
                "{\"source\":\"r\",\"target\":\"e\"}]}";

        MicroflowContext ctx = run(definition);
        assertThat(ctx.getResult()).isEqualTo(30);
        assertThat(ctx.getVariable("x")).isEqualTo(3);
    }

    @Test
    void shouldFollowConditionTrueBranch() {
        String definition = "{\"nodes\":[" +
                "{\"id\":\"s\",\"type\":\"START\"}," +
                "{\"id\":\"a\",\"type\":\"ASSIGN\",\"config\":{\"target\":\"age\",\"expression\":\"18\"}}," +
                "{\"id\":\"c\",\"type\":\"CONDITION\",\"config\":{\"expression\":\"age >= 18\",\"trueBranch\":\"r1\",\"falseBranch\":\"r2\"}}," +
                "{\"id\":\"r1\",\"type\":\"RETURN\",\"config\":{\"expression\":\"'adult'\"}}," +
                "{\"id\":\"r2\",\"type\":\"RETURN\",\"config\":{\"expression\":\"'minor'\"}}]," +
                "\"edges\":[" +
                "{\"source\":\"s\",\"target\":\"a\"}," +
                "{\"source\":\"a\",\"target\":\"c\"}]}";

        MicroflowContext ctx = run(definition);
        assertThat(ctx.getResult()).isEqualTo("adult");
    }

    @Test
    void shouldFollowConditionFalseBranch() {
        String definition = "{\"nodes\":[" +
                "{\"id\":\"s\",\"type\":\"START\"}," +
                "{\"id\":\"a\",\"type\":\"ASSIGN\",\"config\":{\"target\":\"age\",\"expression\":\"15\"}}," +
                "{\"id\":\"c\",\"type\":\"CONDITION\",\"config\":{\"expression\":\"age >= 18\",\"trueBranch\":\"r1\",\"falseBranch\":\"r2\"}}," +
                "{\"id\":\"r1\",\"type\":\"RETURN\",\"config\":{\"expression\":\"'adult'\"}}," +
                "{\"id\":\"r2\",\"type\":\"RETURN\",\"config\":{\"expression\":\"'minor'\"}}]," +
                "\"edges\":[" +
                "{\"source\":\"s\",\"target\":\"a\"}," +
                "{\"source\":\"a\",\"target\":\"c\"}]}";

        MicroflowContext ctx = run(definition);
        assertThat(ctx.getResult()).isEqualTo("minor");
    }

    @Test
    void shouldUseInputVariables() {
        String definition = "{\"nodes\":[" +
                "{\"id\":\"s\",\"type\":\"START\"}," +
                "{\"id\":\"r\",\"type\":\"RETURN\",\"config\":{\"expression\":\"name + ' says hi'\"}}]," +
                "\"edges\":[{\"source\":\"s\",\"target\":\"r\"}]}";

        MicroflowContext ctx = engine.execute(1L, "test", definition, Map.of("name", "Alice"));
        assertThat(ctx.getResult()).isEqualTo("Alice says hi");
    }

    @Test
    void shouldThrowExceptionFromThrowExceptionNode() {
        String definition = "{\"nodes\":[" +
                "{\"id\":\"s\",\"type\":\"START\"}," +
                "{\"id\":\"t\",\"type\":\"THROW_EXCEPTION\",\"config\":{\"errorMessage\":\"boom\",\"errorCode\":\"E001\"}}]," +
                "\"edges\":[{\"source\":\"s\",\"target\":\"t\"}]}";

        assertThatThrownBy(() -> run(definition))
                .isInstanceOf(MicroflowExecutionException.class)
                .hasMessage("boom")
                .extracting("errorCode").isEqualTo("E001");
    }

    @Test
    void shouldCallSubMicroflowAndStoreResult() {
        LowCodeMicroflowService mockService = mock(LowCodeMicroflowService.class);
        Map<String, Object> subResult = Map.of("result", 42);
        when(mockService.execute(eq("sub"), eq(Map.of()))).thenReturn(subResult);

        MicroflowEngine engineWithCall = new MicroflowEngine(objectMapper, List.of(
                new StartEndExecutor(),
                new CallMicroflowExecutor(sandbox, mockService),
                new ReturnExecutor(sandbox)), executionLogMapper);

        String definition = "{\"nodes\":[" +
                "{\"id\":\"s\",\"type\":\"START\"}," +
                "{\"id\":\"c\",\"type\":\"CALL_MICROFLOW\",\"config\":{\"microflowCode\":\"sub\"}}," +
                "{\"id\":\"r\",\"type\":\"RETURN\",\"config\":{\"expression\":\"_microflowResult\"}}]," +
                "\"edges\":[" +
                "{\"source\":\"s\",\"target\":\"c\"}," +
                "{\"source\":\"c\",\"target\":\"r\"}]}";

        MicroflowContext ctx = engineWithCall.execute(1L, "test", definition, Map.of());
        assertThat(ctx.getVariable("_microflowResult")).isEqualTo(subResult);
        assertThat(ctx.getResult()).isEqualTo(subResult);
    }

    @Test
    void shouldExecuteLoopOverIterable() {
        MicroflowEngine engineWithLoop = new MicroflowEngine(objectMapper, List.of(
                new StartEndExecutor(),
                new AssignExecutor(sandbox),
                new LoopExecutor(sandbox),
                new ReturnExecutor(sandbox)), executionLogMapper);

        String definition = "{\"nodes\":[" +
                "{\"id\":\"s\",\"type\":\"START\"}," +
                "{\"id\":\"init\",\"type\":\"ASSIGN\",\"config\":{\"target\":\"collected\",\"expression\":\"[]\"}}," +
                "{\"id\":\"loop\",\"type\":\"LOOP\",\"config\":{\"iterableExpression\":\"[1, 2, 3]\",\"bodyNodeId\":\"body\"}}," +
                "{\"id\":\"body\",\"type\":\"ASSIGN\",\"config\":{\"target\":\"collected\",\"expression\":\"collected + [_loopItem]\"}}," +
                "{\"id\":\"r\",\"type\":\"RETURN\",\"config\":{\"expression\":\"collected\"}}]," +
                "\"edges\":[" +
                "{\"source\":\"s\",\"target\":\"init\"}," +
                "{\"source\":\"init\",\"target\":\"loop\"}," +
                "{\"source\":\"loop\",\"target\":\"r\"}," +
                "{\"source\":\"body\",\"target\":\"loop\"}]}";

        MicroflowContext ctx = engineWithLoop.execute(1L, "test", definition, Map.of());
        assertThat(ctx.getResult()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void shouldSkipLoopWhenIterableEmpty() {
        MicroflowEngine engineWithLoop = new MicroflowEngine(objectMapper, List.of(
                new StartEndExecutor(),
                new AssignExecutor(sandbox),
                new LoopExecutor(sandbox),
                new ReturnExecutor(sandbox)), executionLogMapper);

        String definition = "{\"nodes\":[" +
                "{\"id\":\"s\",\"type\":\"START\"}," +
                "{\"id\":\"init\",\"type\":\"ASSIGN\",\"config\":{\"target\":\"hit\",\"expression\":\"0\"}}," +
                "{\"id\":\"loop\",\"type\":\"LOOP\",\"config\":{\"iterableExpression\":\"[]\",\"bodyNodeId\":\"body\"}}," +
                "{\"id\":\"body\",\"type\":\"ASSIGN\",\"config\":{\"target\":\"hit\",\"expression\":\"hit + 1\"}}," +
                "{\"id\":\"r\",\"type\":\"RETURN\",\"config\":{\"expression\":\"hit\"}}]," +
                "\"edges\":[" +
                "{\"source\":\"s\",\"target\":\"init\"}," +
                "{\"source\":\"init\",\"target\":\"loop\"}," +
                "{\"source\":\"loop\",\"target\":\"r\"}," +
                "{\"source\":\"body\",\"target\":\"loop\"}]}";

        MicroflowContext ctx = engineWithLoop.execute(1L, "test", definition, Map.of());
        // 空 iterable 时循环体不应执行，hit 保持初始值 0
        assertThat(ctx.getResult()).isEqualTo(0);
        assertThat(ctx.getVariable("hit")).isEqualTo(0);
    }
}
