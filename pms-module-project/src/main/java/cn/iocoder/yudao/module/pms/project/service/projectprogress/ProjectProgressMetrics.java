package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ProjectProgressMetrics {
    private final MeterRegistry meterRegistry;

    public void snapshot(String status, int missingCount, long elapsedNanos) {
        meterRegistry.counter("pms.project.progress.snapshot", "status", status).increment();
        meterRegistry.timer("pms.project.progress.snapshot.duration", "status", status)
                .record(Duration.ofNanos(elapsedNanos));
        meterRegistry.summary("pms.project.progress.snapshot.missing").record(missingCount);
        if ("PENDING".equals(status)) {
            meterRegistry.counter("pms.project.progress.pending").increment();
        }
    }

    public void approvalCallback(String result) {
        meterRegistry.counter("pms.project.progress.policy.callback", "result", result).increment();
    }
}
