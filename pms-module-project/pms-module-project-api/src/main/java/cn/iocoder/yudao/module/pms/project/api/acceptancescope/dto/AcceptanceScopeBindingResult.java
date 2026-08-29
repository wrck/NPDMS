package cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto;

import java.util.List;

public record AcceptanceScopeBindingResult(
        boolean replayed,
        Integer acceptanceFactVersion,
        List<AcceptanceScopeBindingFact> bindings) {
}
