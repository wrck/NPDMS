package cn.iocoder.yudao.module.pms.platform.api.guard;

/** 提供方拥有的项目治理只读守卫接口。 */
public interface ProjectGovernanceGuardProviderApi {

    String providerCode();

    ProjectGovernanceProviderFact inspect(ProjectGovernanceGuardQuery query);
}
