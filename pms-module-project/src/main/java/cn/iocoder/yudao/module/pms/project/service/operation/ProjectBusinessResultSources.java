package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.*;
import org.springframework.stereotype.Component;

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
