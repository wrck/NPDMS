package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 实际Compiler/Reader/工厂；仅隔离尚未启用的订阅发布检查，不能将夹具作为生产发布成功证据。 */
final class ResultSubscriptionTaskFixture {
    static TemplateDesignerDocument designer() {
        var source=new TemplateDesignerDocument();var stage=new TemplateDesignerDocument.StageNode();
        stage.setNodeKey("stage");stage.setCode("S1");stage.setName("阶段");stage.setLifecycleStage("S1");stage.setStart(true);stage.setTerminal(true);stage.setCompletionRule(rule());source.getStages().add(stage);
        var task=new TemplateDesignerDocument.TaskNode();task.setNodeKey("task");task.setCode("T1");task.setName("结果任务");task.setStageCode("S1");task.setCompletionRule(rule());
        task.setExecution(JsonUtils.parseTree("{\"subscriptions\":[{\"key\":\"source\",\"ownerContext\":\"TEST\",\"entityType\":\"RESULT\",\"resultType\":\"FORMED\",\"scope\":{\"mode\":\"PROJECT\"},\"policy\":{\"acquisition\":\"REUSE_EXISTING\",\"validity\":\"CURRENT_VALID\",\"selection\":\"EXACT_ONE\"}}]}"));
        source.getTasks().add(task);return source;
    }
    static TemplateCompiler compiler() {
        var compiler=new TemplateCompiler();var configuration=mock(TemplateExecutionConfigurationCompilation.class);
        when(configuration.prepare(any())).thenReturn(List.of());ReflectionTestUtils.setField(compiler,"executionConfigurations",configuration);return compiler;
    }
    static TemplateExecutionSnapshot snapshot() {
        var result=compiler().compileVersioned(designer());assertTrue(result.valid(),()->result.issues().toString());
        assertNull(result.snapshotHash());return result.snapshot();
    }
    static ProjectTaskExecutionContractDO contract(TemplateExecutionSnapshot snapshot) {
        var result=new TaskExecutionContractFactory().create(4L,99L,snapshot.toRuntimeContent().getTasks().getFirst(),LocalDateTime.of(2026,9,18,0,0));
        result.setId(30L);result.setTenantId(1L);return result;
    }
    private static TemplateDesignerDocument.RuleSpec rule() {
        var rule=new TemplateDesignerDocument.RuleSpec();rule.setExpression(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"));return rule;
    }
}
