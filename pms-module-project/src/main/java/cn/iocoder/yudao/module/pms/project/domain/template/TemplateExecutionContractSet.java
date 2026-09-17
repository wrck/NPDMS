package cn.iocoder.yudao.module.pms.project.domain.template;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact node/instance/contract membership for one node kind; caller retains scope authorization. */
public final class TemplateExecutionContractSet {
    private TemplateExecutionContractSet() { }

    public record Identity(String nodeKey, Long instanceId, Long contractId) { }

    public static void requireExact(List<String> expectedKeys, List<Identity> contracts) {
        if (expectedKeys == null || contracts == null || expectedKeys.size() != contracts.size()) {
            throw invalid();
        }
        Set<String> remaining = new HashSet<>();
        for (String key : expectedKeys) {
            if (key == null || key.isBlank() || !remaining.add(key)) throw invalid();
        }
        Set<Long> instances = new HashSet<>();
        Set<Long> contractIds = new HashSet<>();
        for (Identity contract : contracts) {
            if (contract == null || contract.nodeKey() == null || !remaining.remove(contract.nodeKey())
                    || contract.instanceId() == null || contract.instanceId() <= 0
                    || contract.contractId() == null || contract.contractId() <= 0
                    || !instances.add(contract.instanceId()) || !contractIds.add(contract.contractId())) {
                throw invalid();
            }
        }
        if (!remaining.isEmpty()) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("FROZEN_CONTRACT_SET_INCOMPLETE");
    }
}
