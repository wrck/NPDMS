package cn.iocoder.yudao.module.pms.project.service.rule;

import org.flowable.common.engine.impl.de.odysseus.el.ExpressionFactoryImpl;
import org.flowable.common.engine.impl.el.DefaultExpressionManager;
import org.flowable.dmn.engine.DmnEngine;
import org.flowable.dmn.engine.DmnEngineConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Properties;

@Configuration(proxyBeanMethods = false)
public class ProjectDecisionEngineConfiguration {
    @Bean(destroyMethod = "close")
    public DmnEngine projectDecisionEngine() {
        // Flowable's native non-relational mode: frozen project definitions remain the only rule store.
        var configuration = DmnEngineConfiguration.createStandaloneDmnEngineConfiguration();
        configuration.setEngineName("pms-template-decisions");
        configuration.setUsingRelationalDatabase(false);
        configuration.setUsingSchemaMgmt(false);
        configuration.setHistoryEnabled(false);
        configuration.setEnableConfiguratorServiceLoader(false);
        configuration.setBeans(Map.of());

        var properties = new Properties();
        properties.setProperty(ExpressionFactoryImpl.PROP_METHOD_INVOCATIONS, "false");
        var expressions = new DefaultExpressionManager(Map.of());
        expressions.setExpressionFactory(new ExpressionFactoryImpl(properties));
        configuration.setExpressionManager(expressions);
        return configuration.buildDmnEngine();
    }
}
