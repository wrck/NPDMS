package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import org.springframework.stereotype.Component;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChangeSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import java.util.Comparator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact result-type routing, independent of operation handlers and presentation bindings. */
@Component
public final class ProjectBusinessResultSources {
    private record Registered(Descriptor descriptor, BusinessResultSource source) { }
    private final Map<Type, Registered> sources;

    public ProjectBusinessResultSources(List<BusinessResultSource> providers) {
        var collected = new LinkedHashMap<Type, Registered>();
        for (var provider : providers) {
            var descriptor = Objects.requireNonNull(provider.descriptor(), "result descriptor");
            if (collected.putIfAbsent(descriptor.type(), new Registered(descriptor, provider)) != null)
                throw new IllegalArgumentException("RESULT_SOURCE_DUPLICATE");
        }
        sources = Map.copyOf(collected);
    }

    public Descriptor descriptor(Type type) {
        var found = sources.get(type);
        return found == null ? null : found.descriptor();
    }

    public List<Type> changeTypes(BusinessOperationResultEvent event) {
        return sources.values().stream().filter(value -> value.source() instanceof BusinessResultChangeSource)
                .map(value -> value.descriptor().type())
                .filter(type -> type.ownerContext().equals(event.ownerContext()) && type.entityType().equals(event.objectType()))
                .sorted(Comparator.comparing(Type::resultType)).toList();
    }

    public boolean changeSupported(Type type) {
        var value = sources.get(type);
        return value != null && value.source() instanceof BusinessResultChangeSource;
    }

    public Query changeQuery(Type type, BusinessOperationResultEvent event) {
        var query = changeSource(type).changeQuery(event);
        if (query == null || !type.equals(query.type()) || !Objects.equals(event.tenantId(), query.tenantId())
                || !Objects.equals(event.projectId(), query.projectId()))
            throw new IllegalStateException("RESULT_EVENT_QUERY_MISMATCH");
        return query;
    }

    public boolean declaresFormation(Type type, BusinessOperationResultEvent event) {
        return changeSource(type).declaresFormation(event);
    }

    private BusinessResultChangeSource changeSource(Type type) {
        var registered = sources.get(type);
        if (registered == null || !(registered.source() instanceof BusinessResultChangeSource changes))
            throw new IllegalArgumentException("RESULT_CHANGE_SOURCE_UNAVAILABLE");
        return changes;
    }

    public Observation inspect(Query query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        var registered = sources.get(query.type());
        if (registered == null) throw new IllegalArgumentException("RESULT_SOURCE_UNAVAILABLE");
        if (query.resultId() == null ? !registered.descriptor().currentLookup() : !registered.descriptor().exactLookup())
            throw new IllegalArgumentException("RESULT_LOOKUP_UNSUPPORTED");
        var observation = Objects.requireNonNull(registered.source().inspect(query), "Owner result observation");
        if (observation.result() != null) {
            var result = observation.result();
            if (!Objects.equals(query.tenantId(), result.tenantId()) || !Objects.equals(query.projectId(), result.projectId())
                    || !Objects.equals(query.type(), result.type())
                    || query.objectId() != null && !query.objectId().equals(result.objectId())
                    || query.resultId() != null && !query.resultId().equals(result.resultId()))
                throw new IllegalStateException("RESULT_SOURCE_IDENTITY_MISMATCH");
        }
        return observation;
    }
}
