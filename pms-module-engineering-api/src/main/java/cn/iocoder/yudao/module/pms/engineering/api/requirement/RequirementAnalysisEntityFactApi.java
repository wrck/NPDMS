package cn.iocoder.yudao.module.pms.engineering.api.requirement;

import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFileFact;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisWorkBindingFact;
import java.time.LocalDateTime;
import java.util.*;

/** Structured completed business content, explicitly located by entity and revision. */
public interface RequirementAnalysisEntityFactApi {
    Fact inspect(Query query);
    Fact lockAndRevalidate(Fact expected);

    record Query(Long projectId, Long entityId, Long revisionId) {}
    record Form(Long revisionId, int bindingVersion, Long extensionDefinitionRevisionId,
                Map<String, String> fieldBindings) {}
    record Fact(Long projectId, Long entityId, Long revisionId, int revisionNo, int version,
                Integer projectVersion, Long projectTemplateRevisionId,
                RequirementAnalysisWorkBindingFact workBinding, Form form,
                Long extensionDefinitionRevisionId, int extensionValueVersion,
                Map<String, Object> values, List<RequirementAnalysisFileFact> files,
                Long frozenBy, LocalDateTime frozenAt, Long effectiveRevisionId) {
        public Fact {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            files = List.copyOf(files);
        }
    }
}
