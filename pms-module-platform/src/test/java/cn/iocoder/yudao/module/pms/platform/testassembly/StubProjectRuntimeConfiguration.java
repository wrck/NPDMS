package cn.iocoder.yudao.module.pms.platform.testassembly;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 测试装配桩：本应用不装配具体项目运行时，任何项目范围请求都显式报告能力不可用，
 * 不伪造授权结果；仅交付件归集等未在本次闭环中使用的能力会触达此处。
 */
@Configuration(proxyBeanMethods = false)
public class StubProjectRuntimeConfiguration {

    @Bean
    public ProjectScopeApi projectScopeApi() {
        return new ProjectScopeApi() {
            private BusinessContractException absent() {
                return new BusinessContractException("PROJECT_RUNTIME_ABSENT",
                        "本应用未装配项目运行时，项目范围能力不可用");
            }

            @Override
            public ProjectScopeResult resolve(ProjectScopeQuery query) {
                throw absent();
            }

            @Override
            public ProjectScopeResult resolveCurrent(ProjectCurrentScopeQuery query) {
                throw absent();
            }

            @Override
            public java.util.Set<Long> resolveAllCurrent(ProjectAllScopeQuery query) {
                throw absent();
            }

            @Override
            public ProjectScopeResult lockAndRevalidate(ProjectScopeRevalidationQuery query) {
                throw absent();
            }
        };
    }
}
