package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.projection;

import lombok.Data;

@Data
public class ProjectStatistics {
    private long total;
    private long overdue;
    private long s0;
    private long s1;
    private long s2;
    private long s3;
    private long s4;
    private long s5;
    private long s6;
    private long normalClosed;
    private long noTrackingClosed;
    private long exceptionClosed;
}
