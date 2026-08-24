package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class ProjectSplitMetrics {
    private final MeterRegistry meterRegistry;

    public void preview(boolean success, String failureType, long elapsedNanos) {
        meterRegistry.counter("pms.project.split.preview", "result", success ? "success" : "failure",
                "failure_type", success ? "none" : failureType).increment();
        meterRegistry.timer("pms.project.split.preview.duration", "result", success ? "success" : "failure")
                .record(Duration.ofNanos(elapsedNanos));
    }

    public void apply(boolean success, String failureStage, long elapsedNanos) {
        meterRegistry.counter("pms.project.split.apply", "result", success ? "success" : "failure",
                "failure_stage", success ? "none" : failureStage).increment();
        meterRegistry.timer("pms.project.split.apply.duration", "result", success ? "success" : "failure")
                .record(Duration.ofNanos(elapsedNanos));
    }
}
