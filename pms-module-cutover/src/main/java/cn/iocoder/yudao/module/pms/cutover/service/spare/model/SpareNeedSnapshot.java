package cn.iocoder.yudao.module.pms.cutover.service.spare.model;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** CUT-08从当前P2/P3事实形成的不可变需求来源。 */
public record SpareNeedSnapshot(boolean required, List<NeedSource> sources) {

    public SpareNeedSnapshot {
        sources = sources == null ? null : sources.stream().sorted(Comparator
                .comparing(NeedSource::sourceType).thenComparing(NeedSource::sourceId)).toList();
        require(sources != null, "sources");
        require(required == !sources.isEmpty(), "required");
        Set<String> identities = new HashSet<>();
        for (NeedSource source : sources) {
            require(source != null && identities.add(source.sourceType() + ":" + source.sourceId()), "sources");
        }
    }

    public sealed interface NeedSource permits AssessmentNeedSource, ChecklistRiskNeedSource {
        String sourceType();
        Long sourceId();
        Integer sourceVersion();
    }

    public record AssessmentNeedSource(Long sourceId, Integer sourceVersion,
                                       Boolean sparePartApplied) implements NeedSource {
        public AssessmentNeedSource {
            positive(sourceId, "sourceId");
            require(sourceVersion != null && sourceVersion > 0, "sourceVersion");
            require(Boolean.TRUE.equals(sparePartApplied), "sparePartApplied");
        }
        @Override public String sourceType() { return "ASSESSMENT"; }
    }

    public record ChecklistRiskNeedSource(Long sourceId, Integer sourceVersion,
                                          String stableItemKey, Boolean applicable) implements NeedSource {
        public ChecklistRiskNeedSource {
            positive(sourceId, "sourceId");
            require(sourceVersion != null && sourceVersion >= 0, "sourceVersion");
            require("MAJOR_PROJECT_SPARES".equals(stableItemKey), "stableItemKey");
            require(Boolean.TRUE.equals(applicable), "applicable");
        }
        @Override public String sourceType() { return "CHECKLIST_RISK"; }
    }

    private static void positive(Long value, String field) {
        require(value != null && value > 0, field);
    }

    private static void require(boolean condition, String field) {
        if (!condition) throw new IllegalArgumentException("invalid " + field);
    }
}
