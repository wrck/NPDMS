package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.support.access.OwnerTenantReadScopePolicy;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

/** Read-only compatibility, each catalog's actual Owner query contract retains its native permission. */
@Configuration(proxyBeanMethods=false)
public class LegacyTenantReadBindings {
    @Bean public OwnerTenantReadScopePolicy legacyTenantCatalogReads(BusinessModelCatalog catalog,BusinessEntityPersistenceRegistry persistence) {
        // ACC: SatisfactionTemplateManagementService.list(tenantId), SatisfactionTemplateController.list.
        // PLT: FormTemplateServiceImpl.getFormTemplate/Page, FormTemplateController.get/page.
        // KNO: AnnouncementServiceImpl.getAnnouncement/Page, AnnouncementController.get/page.
        // PRJ: StageSuggestionRuleServiceImpl.getRule/Page, StageSuggestionRuleController.get/page.
        return new OwnerTenantReadScopePolicy(Set.of("ACC/satisfactionQuestionnaireTemplate","PLT/formTemplate",
            "KNO/announcement","PRJ/stageSuggestionRule"),catalog,persistence);
    }
}
