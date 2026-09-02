package cn.iocoder.yudao.module.pms.cutover.service.dashboard;

import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.port.CutoverDashboardActionFactPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic test-only action facts for the controlled dashboard positive loop. */
public final class ControlledCutoverDashboardActionFactPort implements CutoverDashboardActionFactPort {
    private final Map<Long, CutoverDashboardActionFacts> factsByTaskId;

    public ControlledCutoverDashboardActionFactPort(List<CutoverDashboardActionFacts> facts) {
        LinkedHashMap<Long, CutoverDashboardActionFacts> indexed = new LinkedHashMap<>();
        for (CutoverDashboardActionFacts fact : facts) {
            if (fact == null || fact.taskId() == null || fact.taskId() <= 0
                    || fact.facts() == null || indexed.putIfAbsent(fact.taskId(), fact) != null) {
                throw new IllegalArgumentException("controlled dashboard facts are invalid");
            }
        }
        this.factsByTaskId = Map.copyOf(indexed);
    }

    @Override
    public List<CutoverDashboardActionFacts> inspectBatch(BatchQuery query) {
        return query.candidates().stream().map(candidate -> {
            CutoverDashboardActionFacts fact = factsByTaskId.get(candidate.taskId());
            if (fact == null) throw new IllegalStateException("controlled dashboard fact is missing");
            return fact;
        }).toList();
    }
}
