package com.dp.deviceops.adapter.web.schedule;

import com.dp.deviceops.core.model.InspectionSchedule;
import com.dp.deviceops.core.port.InspectionSchedulePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.support.CronExpression;
import java.time.*;
import java.util.UUID;

@Component @ConditionalOnProperty(prefix="device-ops.schedule",name="enabled",havingValue="true") public final class ScheduleDispatcher {
 private static final Logger log=LoggerFactory.getLogger(ScheduleDispatcher.class); private final InspectionSchedulePort schedules; private final ScheduleProperties properties; private final Clock clock=Clock.systemUTC();
 public ScheduleDispatcher(InspectionSchedulePort schedules,ScheduleProperties properties){this.schedules=schedules;this.properties=properties;properties.validate();}
 @Scheduled(fixedDelayString="${device-ops.schedule.fixed-delay:30s}") public void dispatch(){Instant now=clock.instant();String worker=UUID.randomUUID().toString();for(InspectionSchedule s:schedules.claimDue(worker,now,now.plus(properties.getLease()),properties.getBatchSize()))try{schedules.completeDue(s,worker,s.nextRunAt(),next(s,now));}catch(RuntimeException completion){try{schedules.failDue(s,worker,"DISPATCH_FAILED");}catch(RuntimeException leaseFailure){log.warn("Schedule failure state not persisted scheduleKey={} exceptionType={}",s.scheduleKey(),leaseFailure.getClass().getSimpleName());}}}
 private static Instant next(InspectionSchedule s,Instant base){Instant result=CronExpression.parse(s.cron()).next(base.atZone(ZoneId.of(s.timezone()))).toInstant();if(!result.isAfter(base))throw new IllegalArgumentException("next run must be in the future");return result;}
}
