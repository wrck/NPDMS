package cn.iocoder.yudao.module.pms.project.service.rule;

import com.yomahub.liteflow.flow.FlowBus;
import com.yomahub.liteflow.springboot4.config.LiteflowMainAutoConfiguration;
import com.yomahub.liteflow.springboot4.config.LiteflowPropertyAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/** In-process native engine only. This configuration imports no business infrastructure or application profile. */
public final class RuleEngineTestFixture implements AutoCloseable {
    private final AnnotationConfigApplicationContext application = new AnnotationConfigApplicationContext();

    public RuleEngineTestFixture() {
        application.getEnvironment().getPropertySources().addFirst(new MapPropertySource("rule-test", Map.of(
                "liteflow.print-banner", false, "liteflow.monitor.enable-log", false,
                "liteflow.print-execution-log", false, "liteflow.parse-mode", "PARSE_ALL_ON_START")));
        application.register(EngineConfiguration.class);
        application.refresh();
        if (!FlowBus.containNode("pmsRulePrepare")) throw new IllegalStateException("Rule components not registered");
    }

    public ProjectRuleEvaluationService evaluator() {
        return application.getBean(ProjectRuleEvaluationService.class);
    }

    public <T> T bean(Class<T> type) {
        return application.getBean(type);
    }

    @Override
    public void close() {
        application.close();
        FlowBus.cleanCache();
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({LiteflowPropertyAutoConfiguration.class, LiteflowMainAutoConfiguration.class})
    @Import({ProjectRuleComponents.Prepare.class, ProjectRuleComponents.Decisions.class, ProjectRuleComponents.DecisionValue.class, ProjectRuleComponents.Predicate.class,
            ProjectRuleComponents.Field.class, ProjectRuleComponents.Matched.class,
            ProjectRuleComponents.NotMatched.class, ProjectRuleEvaluationService.class})
    static class EngineConfiguration { }
}
