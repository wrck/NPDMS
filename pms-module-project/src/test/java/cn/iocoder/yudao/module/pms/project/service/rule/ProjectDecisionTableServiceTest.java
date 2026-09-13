package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import org.flowable.dmn.engine.DmnEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProjectDecisionTableServiceTest {
    private static DmnEngine engine;
    private static ProjectDecisionTableService service;

    @BeforeAll static void start() {
        engine = new ProjectDecisionEngineConfiguration().projectDecisionEngine();
        service = new ProjectDecisionTableService(engine);
    }
    @AfterAll static void stop() { if (engine != null) engine.close(); }

    @Test void usesNativeDecisionTablesWithoutOpeningADatabase() {
        assertNull(engine.getDmnEngineConfiguration().getDataSource());
        assertFalse(engine.getDmnEngineConfiguration().isUsingRelationalDatabase());
        var definition = table("100", "true");
        assertEquals("FIRST", service.validate(definition, Set.of("project.amount")).hitPolicy());
        var result = service.evaluate(7L, "template:1", definition, key -> RuleFact.known(new BigDecimal("120")));
        assertTrue(result.available(), result.reasonCode());
        assertEquals(Boolean.TRUE, result.rows().getFirst().get("allowed"));
    }

    @Test void nativeExecutorAcceptsAnInMemoryDecisionModel() {
        byte[] xml = table("100", "true").xml().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var model = new org.flowable.dmn.xml.converter.DmnXMLConverter()
                .convertToDmnModel(() -> new java.io.ByteArrayInputStream(xml), true, true);
        var context = new org.flowable.dmn.api.ExecuteDecisionContext();
        context.setVariables(new java.util.LinkedHashMap<>(Map.of("amount", new BigDecimal("120"))));
        context.setDisableHistory(true);
        var configuration = engine.getDmnEngineConfiguration();
        var command = new org.flowable.common.engine.impl.interceptor.CommandConfig()
                .setContextReusePossible(false).transactionNotSupported();
        var result = configuration.getCommandExecutor().execute(command, commandContext ->
                configuration.getRuleEngineExecutor().execute(model.getDecisionById("eligibility"), context));
        if (Boolean.TRUE.equals(result.isFailed())) throw new AssertionError("Native decision execution failed", result.getException());
        assertEquals(Boolean.TRUE, result.getDecisionResult().getFirst().get("allowed"));
    }

    @Test void frozenXmlNotLatestDecisionKeyDeterminesTheResult() {
        var old = table("100", "true");
        var newer = table("200", "true");
        assertEquals(Boolean.TRUE, service.evaluate(7L, "template:1", old,
                key -> RuleFact.known(120)).rows().getFirst().get("allowed"));
        assertEquals(Boolean.FALSE, service.evaluate(7L, "template:2", newer,
                key -> RuleFact.known(120)).rows().getFirst().get("allowed"));
        assertEquals(Boolean.TRUE, service.evaluate(7L, "template:1", old,
                key -> RuleFact.known(120)).rows().getFirst().get("allowed"));
    }

    @Test void unknownInputNeverUsesTheOtherwiseRow() {
        var result = service.evaluate(7L, "project:1", table("100", "true"), key -> RuleFact.unknown("SOURCE_OFFLINE"));
        assertFalse(result.available());
        assertEquals("DECISION_INPUT_UNAVAILABLE", result.reasonCode());
        assertTrue(result.rows().isEmpty());
    }

    @Test void rejectsUnmappedFieldsAndMethodCallsDuringValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(table("100", "true"), Set.of()));
        assertThrows(RuntimeException.class, () -> service.validate(table("100", "amount.toString()"), Set.of("project.amount")));
    }

    @Test void successfulExecutionWithoutAResultRemainsUnknown() {
        var source = table("100", "true");
        var xml = source.xml().replace("<rule id=\"otherwise\"><inputEntry id=\"i2\"><text>-</text></inputEntry><outputEntry id=\"o2\"><text>false</text></outputEntry></rule>", "");
        var result = service.evaluate(7L, "plan:3", new DecisionTableDefinition(source.key(), source.name(),
                source.decisionKey(), xml, source.inputFields()), key -> RuleFact.known(1));
        assertFalse(result.available());
        assertEquals("DECISION_NO_RESULT", result.reasonCode());
    }

    static DecisionTableDefinition table(String threshold, String output) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="definition" name="准入策略" namespace="pms" expressionLanguage="juel">
                  <decision id="eligibility" name="准入策略">
                    <decisionTable id="decisionTable" hitPolicy="FIRST">
                      <input id="inputAmount" label="金额"><inputExpression id="amountExpression" typeRef="number"><text>amount</text></inputExpression></input>
                      <output id="allowedOutput" name="allowed" label="允许" typeRef="boolean"/>
                      <rule id="matched"><inputEntry id="i1"><text><![CDATA[>= %s]]></text></inputEntry><outputEntry id="o1"><text>%s</text></outputEntry></rule>
                      <rule id="otherwise"><inputEntry id="i2"><text>-</text></inputEntry><outputEntry id="o2"><text>false</text></outputEntry></rule>
                    </decisionTable>
                  </decision>
                </definitions>
                """.formatted(threshold, output);
        return new DecisionTableDefinition("eligibility", "准入策略", "eligibility", xml, Map.of("amount", "project.amount"));
    }
}
