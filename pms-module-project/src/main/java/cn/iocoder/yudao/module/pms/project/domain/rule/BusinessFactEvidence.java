package cn.iocoder.yudao.module.pms.project.domain.rule;

import java.util.List;
import java.util.Map;

/** Frozen Owner predicates, not business bodies or a second editable rule source. */
public record BusinessFactEvidence(Long executionId, Long planVersionId, List<Result> results) {
    public BusinessFactEvidence { results = List.copyOf(results); }
    public record Result(Long associationId, String objectId, String factVersion, Map<String, Boolean> facts) {
        public Result { facts = Map.copyOf(facts); }
    }
}
