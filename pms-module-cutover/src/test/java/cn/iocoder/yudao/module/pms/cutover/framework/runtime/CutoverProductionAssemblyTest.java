package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi;
import cn.iocoder.yudao.module.pms.cutover.api.task.CutoverTaskIntakeApi;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverConfigurationService;
import cn.iocoder.yudao.module.pms.cutover.service.configuration.CutoverRuntimeConfigurationService;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileBusinessObjectPolicyProvider;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.beans.Introspector;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Checks the production component graph and route uniqueness without touching a running service. */
class CutoverProductionAssemblyTest {
    private static final String BASE = "cn.iocoder.yudao.module.pms.cutover";

    @Test
    void productionComponentsAndStageRoutesAreAssembled() throws Exception {
        Set<Class<?>> boundaries = new LinkedHashSet<>();
        var resources = new PathMatchingResourcePatternResolver();
        var metadata = new CachingMetadataReaderFactory(resources);
        for (var resource : resources.getResources("classpath*:" + BASE.replace('.', '/') + "/**/*.class")) {
            if (resource.getURL().getPath().contains("/test-classes/")) continue;
            String name = metadata.getMetadataReader(resource).getClassMetadata().getClassName();
            if (name.contains("$")) continue;
            Class<?> type = ClassUtils.forName(name, getClass().getClassLoader());
            if (type.isInterface() && name.startsWith(BASE + ".dal.mysql.")) boundaries.add(type);
            for (var constructor : type.getDeclaredConstructors()) {
                for (Class<?> dependency : constructor.getParameterTypes()) addExternalApi(boundaries, dependency);
            }
            for (var field : type.getDeclaredFields()) addExternalApi(boundaries, field.getType());
            for (var method : type.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Bean.class)) continue;
                for (Class<?> dependency : method.getParameterTypes()) addExternalApi(boundaries, dependency);
            }
        }
        boundaries.remove(FileArtifactApi.class);
        new WebApplicationContextRunner().withUserConfiguration(ProductionScan.class)
                .withConfiguration(AutoConfigurations.of(TransactionAutoConfiguration.class))
                .withInitializer(context -> {
                    for (Class<?> boundary : boundaries) {
                        var definition = new RootBeanDefinition(boundary);
                        definition.setInstanceSupplier(() -> mock(boundary));
                        ((BeanDefinitionRegistry) context.getBeanFactory()).registerBeanDefinition(
                                Introspector.decapitalize(boundary.getSimpleName()), definition);
                    }
                }).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CutoverTaskIntakeApi.class)
                            .hasSingleBean(CutoverApprovalFactApi.class).hasSingleBean(CutoverResultApi.class);
                    assertThat(context.getBean(CutoverConfigurationService.class))
                            .isInstanceOf(CutoverRuntimeConfigurationService.class);
                    var routes = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet()
                            .stream().flatMap(route -> route.getPatternValues().stream()).collect(java.util.stream.Collectors.toSet());
                    assertThat(routes).contains("/api/v1/pms/cutover-tasks", "/api/v1/pms/cutover-tasks/{taskId}/checklist",
                            "/api/v1/pms/cutover-tasks/{taskId}/plan", "/api/v1/pms/cutover-tasks/{taskId}/closure",
                            "/api/v1/pms/cutover-config/approval-workflow");
                });
    }

    private static void addExternalApi(Set<Class<?>> boundaries, Class<?> type) {
        String name = type.getName();
        if (type.isInterface() && name.startsWith("cn.iocoder.yudao.module.")
                && !name.startsWith(BASE + ".") && name.contains(".api.")) boundaries.add(type);
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = BASE, excludeFilters = @ComponentScan.Filter(
            type = FilterType.CUSTOM, classes = TestOutputFilter.class))
    @EnableWebMvc
    @EnableTransactionManagement(proxyTargetClass = true)
    static class ProductionScan {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().findAndAddModules().build(); }
        @Bean PlatformTransactionManager transactionManager() { return mock(PlatformTransactionManager.class); }
        @Bean FileArtifactApi fileArtifactApi(List<FileBusinessObjectPolicyProvider> policies) {
            // The real FileArtifactApi is constructed through the registry of business policies.
            assertThat(policies).hasSize(3);
            return mock(FileArtifactApi.class);
        }
    }

    static class TestOutputFilter implements org.springframework.core.type.filter.TypeFilter {
        @Override
        public boolean match(org.springframework.core.type.classreading.MetadataReader metadata,
                org.springframework.core.type.classreading.MetadataReaderFactory factory) throws java.io.IOException {
            return metadata.getResource().getURL().getPath().contains("/test-classes/");
        }
    }
}
