package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

import java.util.List;

/** 业务域向统一目录贡献声明；框架自动发现并装配，不允许按实体名写死逻辑。 */
public interface BusinessModelContributor {

    List<BusinessModelDeclaration> declarations();
}
