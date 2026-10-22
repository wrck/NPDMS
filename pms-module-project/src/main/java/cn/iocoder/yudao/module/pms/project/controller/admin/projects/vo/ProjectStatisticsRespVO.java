package cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 全部可见项目状态卡统计")
public class ProjectStatisticsRespVO {
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
