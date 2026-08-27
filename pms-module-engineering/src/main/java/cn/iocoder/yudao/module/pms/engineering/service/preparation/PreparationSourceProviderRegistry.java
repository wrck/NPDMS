package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.module.pms.engineering.api.source.PreparationSourceFactProvider;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFact;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.source.dto.PreparationSourceFactRevalidationQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_SOURCE_UNAVAILABLE;

@Component
public class PreparationSourceProviderRegistry {

    private final List<PreparationSourceFactProvider> providers;

    public PreparationSourceProviderRegistry(List<PreparationSourceFactProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public PreparationSourceFact inspect(PreparationSourceFactQuery query) {
        PreparationSourceFact fact = provider(query.sourceTypeCode()).inspect(query);
        requireExact(query.projectId(), query.itemId(), query.sourceTypeCode(), query.sourceObjectType(),
                query.sourceObjectId(), query.sourceReferenceKey(), fact);
        return fact;
    }

    public PreparationSourceFact lockAndRevalidate(PreparationSourceFactRevalidationQuery query) {
        PreparationSourceFact fact = provider(query.sourceTypeCode()).lockAndRevalidate(query);
        requireExact(query.projectId(), query.itemId(), query.sourceTypeCode(), query.sourceObjectType(),
                query.sourceObjectId(), query.sourceReferenceKey(), fact);
        if (!Objects.equals(query.expectedNormalizedResultCode(), fact.normalizedResultCode())
                || !Objects.equals(query.expectedSourceFactVersion(), fact.sourceFactVersion())
                || !Objects.equals(query.expectedSourceWatermark(), fact.sourceWatermark())) {
            throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        }
        return fact;
    }

    private PreparationSourceFactProvider provider(String sourceTypeCode) {
        if (blank(sourceTypeCode)) throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        List<PreparationSourceFactProvider> matches = providers.stream()
                .filter(provider -> sourceTypeCode.equals(provider.sourceTypeCode())).toList();
        if (matches.size() != 1) throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        return matches.getFirst();
    }

    private void requireExact(Long projectId, Long itemId, String sourceTypeCode, String sourceObjectType,
            String sourceObjectId, String sourceReferenceKey, PreparationSourceFact fact) {
        if (fact == null || !Objects.equals(projectId, fact.projectId()) || !Objects.equals(itemId, fact.itemId())
                || !Objects.equals(sourceTypeCode, fact.sourceTypeCode())
                || !Objects.equals(sourceObjectType, fact.sourceObjectType())
                || !Objects.equals(sourceObjectId, fact.sourceObjectId())
                || !Objects.equals(sourceReferenceKey, fact.sourceReferenceKey())
                || blank(fact.normalizedResultCode()) || blank(fact.sourceFactVersion())
                || blank(fact.sourceWatermark())) {
            throw exception(PREPARATION_SOURCE_UNAVAILABLE);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
