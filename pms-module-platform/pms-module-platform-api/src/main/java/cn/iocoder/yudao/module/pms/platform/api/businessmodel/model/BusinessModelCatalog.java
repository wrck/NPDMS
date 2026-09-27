package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;

import java.util.List;
import java.util.Optional;

/**
 * 设计时目录：业务页面、模板配置/发布与可替换执行层读取同一目录版本。
 * 禁止前后端另维护实体、字段或操作清单。
 */
public interface BusinessModelCatalog {

    Optional<BusinessModelDescriptor> find(String ownerModule, String entityType);

    Optional<BusinessModelDescriptor> findByStableCode(String stableCode);

    List<BusinessModelDescriptor> all();

    default BusinessModelDescriptor require(String ownerModule, String entityType) {
        return find(ownerModule, entityType).orElseThrow(() -> new BusinessContractException(
                "MODEL_NOT_REGISTERED", "实体未在统一目录注册: " + ownerModule + "/" + entityType));
    }
}
