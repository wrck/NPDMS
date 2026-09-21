package com.dp.deviceops.adapter.web.schedule;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("device-ops.schedule") public class ScheduleProperties {
    private boolean enabled=false; private Duration fixedDelay=Duration.ofSeconds(30), lease=Duration.ofSeconds(60); private int batchSize=20;
    public void validate(){if(fixedDelay==null||fixedDelay.isNegative()||fixedDelay.isZero()||lease==null||lease.isNegative()||lease.isZero()||batchSize<=0)throw new IllegalArgumentException("invalid device-ops schedule configuration");}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public Duration getFixedDelay(){return fixedDelay;} public void setFixedDelay(Duration v){fixedDelay=v;} public Duration getLease(){return lease;} public void setLease(Duration v){lease=v;} public int getBatchSize(){return batchSize;} public void setBatchSize(int v){batchSize=v;}
}
