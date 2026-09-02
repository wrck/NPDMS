package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.module.pms.platform.api.export.ExportBusinessDataProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExportBusinessDataProviderRegistry {

    private final Map<String, ExportBusinessDataProvider> providers;

    public ExportBusinessDataProviderRegistry(List<ExportBusinessDataProvider> candidates) {
        Map<String, ExportBusinessDataProvider> indexed = new HashMap<>();
        for (ExportBusinessDataProvider candidate : candidates) {
            String key = key(candidate.ownerContext(), candidate.exportType());
            if (indexed.putIfAbsent(key, candidate) != null) {
                throw new IllegalStateException("统一导出Provider重复: " + key);
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public ExportBusinessDataProvider require(String ownerContext, String exportType) {
        ExportBusinessDataProvider provider = providers.get(key(ownerContext, exportType));
        if (provider == null) {
            throw new ProviderContractException("统一导出Provider缺失");
        }
        return provider;
    }

    private static String key(String ownerContext, String exportType) {
        if (ownerContext == null || ownerContext.isBlank() || exportType == null || exportType.isBlank()) {
            throw new IllegalArgumentException("统一导出Provider键不完整");
        }
        return ownerContext + '\u001f' + exportType;
    }

    public static final class ProviderContractException extends IllegalStateException {
        public ProviderContractException(String message) {
            super(message);
        }
    }
}
