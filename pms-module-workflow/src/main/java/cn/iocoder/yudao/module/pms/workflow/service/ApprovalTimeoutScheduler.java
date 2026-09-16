package cn.iocoder.yudao.module.pms.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.iocoder.yudao.module.pms.platform.api.spi.ProjectConfigProvider;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalHistory;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalHistoryMapper;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批超时扫描器（Story 6）。
 *
 * <p>每小时扫描 {@code timeoutAt < now() AND status = PENDING} 的审批记录，按配置动作处理：</p>
 * <ul>
 *   <li>{@code AUTO_APPROVE}：自动通过（记录状态 → APPROVED）</li>
 *   <li>{@code AUTO_REJECT}：自动拒绝（记录状态 → REJECTED）</li>
 *   <li>{@code NOTIFY_ONLY}（默认）：仅标记 escalated 并记录日志，等待人工处理</li>
 * </ul>
 *
 * <p>配置键 {@code approval.timeout.action}（通过 {@link ProjectConfigProvider} SPI 读取，按项目维度）。
 * 每次处理都追加 {@link ApprovalHistory}（action=TIMEOUT）。</p>
 *
 * <p>关联设计文档：§3.5 审批中心统一规则（行 429-500）、§5.7。需 {@code @EnableScheduling}
 * （pms-admin 已启用）。</p>
 *
 * <p>TD-P8-001：原直接依赖 {@code pms-project} 模块的 {@code ProjectConfigService}，
 * 改为通过 {@link ProjectConfigProvider} SPI 解耦，由 {@code pms-project} 实现并注入。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutScheduler {

    /** 配置键：超时动作。取值 AUTO_APPROVE / AUTO_REJECT / NOTIFY_ONLY */
    public static final String CFG_TIMEOUT_ACTION = "approval.timeout.action";

    /** 默认动作（仅通知）。 */
    public static final String DEFAULT_TIMEOUT_ACTION = "NOTIFY_ONLY";

    private final ApprovalRecordMapper approvalRecordMapper;
    private final ApprovalHistoryMapper approvalHistoryMapper;
    /**
     * 项目配置读取 SPI（TD-P8-001 解耦）。
     * 通过构造器注入；若 pms-project 模块未加载则为 null，{@link #readTimeoutAction(Long)} 回退默认值。
     */
    @Autowired(required = false)
    private ProjectConfigProvider projectConfigProvider;

    /**
     * 每小时整点执行超时扫描（cron: 0 0 * * * ?）。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scanTimeout() {
        LocalDateTime now = LocalDateTime.now();
        List<ApprovalRecord> timeoutRecords = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getStatus, "PENDING")
                        .lt(ApprovalRecord::getTimeoutAt, now));

        if (timeoutRecords.isEmpty()) {
            return;
        }
        log.info("审批超时扫描：发现 {} 条超时待办", timeoutRecords.size());

        for (ApprovalRecord record : timeoutRecords) {
            try {
                handleTimeout(record);
            } catch (Exception e) {
                log.error("处理超时审批失败：recordId={}", record.getId(), e);
            }
        }
    }

    private void handleTimeout(ApprovalRecord record) {
        // 读取项目级超时动作配置
        String action = readTimeoutAction(record.getProjectId());
        String finalStatus;
        switch (action) {
            case "AUTO_APPROVE":
                finalStatus = "APPROVED";
                log.info("超时自动通过：recordId={}", record.getId());
                break;
            case "AUTO_REJECT":
                finalStatus = "REJECTED";
                log.info("超时自动拒绝：recordId={}", record.getId());
                break;
            default:
                // 仅通知：标记 escalated，状态保持 PENDING
                record.setEscalated(true);
                approvalRecordMapper.updateById(record);
                recordHistory(record, "TIMEOUT", "审批超时，已升级通知");
                log.info("超时仅通知（已升级）：recordId={}", record.getId());
                return;
        }

        record.setStatus(finalStatus);
        record.setCompletedAt(LocalDateTime.now());
        record.setEscalated(true);
        approvalRecordMapper.updateById(record);
        recordHistory(record, "TIMEOUT", "审批超时，按配置自动" + ("APPROVED".equals(finalStatus) ? "通过" : "拒绝"));
    }

    private String readTimeoutAction(Long projectId) {
        if (projectId == null || projectConfigProvider == null) {
            return DEFAULT_TIMEOUT_ACTION;
        }
        String value = projectConfigProvider.get(projectId, null, CFG_TIMEOUT_ACTION);
        if (value == null || value.isBlank()) {
            return DEFAULT_TIMEOUT_ACTION;
        }
        return value.toUpperCase();
    }

    private void recordHistory(ApprovalRecord record, String action, String opinion) {
        ApprovalHistory history = ApprovalHistory.builder()
                .recordId(record.getId())
                .round(record.getRound() == null ? 1 : record.getRound())
                .nodeName(record.getCurrentNodeName() == null ? "系统" : record.getCurrentNodeName())
                .operatorId(0L)
                .operatorName("系统超时扫描")
                .action(action)
                .opinion(opinion)
                .operatedAt(LocalDateTime.now())
                .build();
        approvalHistoryMapper.insert(history);
    }
}
