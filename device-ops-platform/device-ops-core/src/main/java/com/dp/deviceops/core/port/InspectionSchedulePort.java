package com.dp.deviceops.core.port;

import com.dp.deviceops.core.model.InspectionSchedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InspectionSchedulePort {
    InspectionSchedule upsert(InspectionSchedule schedule);
    Optional<InspectionSchedule> find(String namespace, String projectKey, String scheduleKey);
    List<InspectionSchedule> list(String namespace, String projectKey);
    void disable(String namespace, String projectKey, String scheduleKey);
    List<InspectionSchedule> claimDue(String workerId, Instant now, Instant leaseUntil, int limit);
    void completeDue(InspectionSchedule schedule, String workerId, Instant dueAt, Instant nextRunAt);
    void failDue(InspectionSchedule schedule, String workerId, String safeStatus);
}
