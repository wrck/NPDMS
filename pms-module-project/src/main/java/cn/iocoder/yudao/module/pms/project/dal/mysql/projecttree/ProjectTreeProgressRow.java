package cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ProjectTreeProgressRow(Long projectId, BigDecimal progress, String status, LocalDateTime recordedAt) { }
