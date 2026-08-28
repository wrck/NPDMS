package cn.iocoder.yudao.module.pms.project.service.projecttree;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ProjectTreeMetrics {
    private final MeterRegistry meterRegistry;

    public void projection(boolean success, long elapsedNanos, int nodeCount) {
        meterRegistry.counter("pms.project.tree.projection", "result", success ? "success" : "failure").increment();
        meterRegistry.timer("pms.project.tree.projection.duration", "result", success ? "success" : "failure")
                .record(Duration.ofNanos(elapsedNanos));
        if (success) {
            meterRegistry.summary("pms.project.tree.projection.nodes").record(nodeCount);
        }
    }

    public void query(String queryType, boolean stale, long elapsedNanos, int nodeCount) {
        meterRegistry.timer("pms.project.tree.query.duration", "query_type", queryType,
                        "stale", Boolean.toString(stale))
                .record(Duration.ofNanos(elapsedNanos));
        meterRegistry.summary("pms.project.tree.query.nodes", "query_type", queryType).record(nodeCount);
        if (stale) {
            meterRegistry.counter("pms.project.tree.query.stale", "query_type", queryType).increment();
        }
    }
}
