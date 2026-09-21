package com.dp.deviceops.parser.runtime.service;

import com.dp.deviceops.parser.semantic.plan.ParserPlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Bounded release-id cache. A cached immutable plan never requires a registry lookup. */
public final class ParserPlanCache {

    private final int capacity;
    private final Map<String, ParserPlan> plans = new LinkedHashMap<>(16, 0.75f, true);

    public ParserPlanCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized ParserPlan getOrLoad(String releaseId, Supplier<ParserPlan> loader) {
        Objects.requireNonNull(releaseId, "releaseId");
        Objects.requireNonNull(loader, "loader");
        ParserPlan cached = plans.get(releaseId);
        if (cached != null) {
            return cached;
        }
        ParserPlan loaded = Objects.requireNonNull(loader.get(), "loaded plan");
        plans.put(releaseId, loaded);
        if (plans.size() > capacity) {
            plans.remove(plans.keySet().iterator().next());
        }
        return loaded;
    }

    public synchronized int size() {
        return plans.size();
    }
}
