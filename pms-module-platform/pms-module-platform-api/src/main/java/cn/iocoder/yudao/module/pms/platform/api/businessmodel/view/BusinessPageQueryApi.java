package cn.iocoder.yudao.module.pms.platform.api.businessmodel.view;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.configuration.BusinessFieldConfigurationApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A business page owns its initial read. Project/runtime consumers never query the Owner's tables. */
public interface BusinessPageQueryApi {
    record Request(@NotNull @Positive Long projectId, @Positive Long initialEntityId,
            String configurationName, String bindingParameters) { }

    record Form(EntityFormApi.Layout layout, EntityExtensionApi.Values extensions,
            List<EntityExtensionApi.Definition> definitions, Map<String, Object> context,
            BusinessEntityData entity) {
        public Form {
            definitions = List.copyOf(definitions);
            context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
        }
    }

    record Page(Request request, BusinessModelViews.ModelDetailVO model,
            BusinessFieldConfigurationApi.Configuration configuration, int pageSize,
            PageResult<BusinessEntityData> records, List<Form> forms,
            List<Page> relatedPages, Map<String, Object> context) {
        public Page {
            forms = List.copyOf(forms);
            relatedPages = List.copyOf(relatedPages);
            context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
        }
    }

    record Read(Page page, ProjectBusinessRuntimeApi.UserInspection inspection) { }
    record View(String ownerModule, String entityType, String stableCode, String apiBase) { }
    record Opened(View view, Page page, ProjectBusinessRuntimeApi.UserInspection inspection) { }

    /** Caller is server-resolved; linked identities are observed from the same rows used for the page. */
    Opened open(ProjectBusinessRuntimeApi.UserContext caller, List<Long> entityIds);
}
