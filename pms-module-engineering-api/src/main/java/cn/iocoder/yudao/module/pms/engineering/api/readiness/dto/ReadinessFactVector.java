package cn.iocoder.yudao.module.pms.engineering.api.readiness.dto;

import java.util.List;

public record ReadinessFactVector(
        Integer inputVersion,
        Long projectScopeVersion,
        List<ReadinessItemFact> itemFacts,
        List<ReadinessFileFact> fileFacts,
        List<ReadinessSourceFact> sourceFacts,
        List<ReadinessWaiverFact> waiverFacts) {

    public ReadinessFactVector {
        itemFacts = List.copyOf(itemFacts == null ? List.of() : itemFacts);
        fileFacts = List.copyOf(fileFacts == null ? List.of() : fileFacts);
        sourceFacts = List.copyOf(sourceFacts == null ? List.of() : sourceFacts);
        waiverFacts = List.copyOf(waiverFacts == null ? List.of() : waiverFacts);
    }
}
