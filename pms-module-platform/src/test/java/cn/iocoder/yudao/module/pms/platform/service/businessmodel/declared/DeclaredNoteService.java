package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationRequest;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import java.util.Map;
import java.util.Locale;

/** Only the business difference is implemented; no mapper, permission, transaction or receipt code. */
@BusinessEntityService(ownerModule = "IT", entityType = "declaredNote")
final class DeclaredNoteService extends ExtensibleBusinessApplicationService {
    DeclaredNoteService(DefaultBusinessApplicationService defaults) { super(defaults); }
    @Override protected Map<String, Object> customOperationChanges(BusinessOperationRequest request, Map<String, Object> values) {
        return switch (request.operationCode()) {
            case "uppercase" -> Map.of("title", ((String) values.get("title")).toUpperCase(Locale.ROOT));
            case "unsafeMove" -> Map.of("projectRef", 100L);
            case "unsafeField" -> Map.of("referenceCode", "forbidden");
            default -> super.customOperationChanges(request, values);
        };
    }
}
