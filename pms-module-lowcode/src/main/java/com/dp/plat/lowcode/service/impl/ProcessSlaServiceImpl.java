package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.entity.LowCodeProcessSlaRecord;
import com.dp.plat.lowcode.mapper.LowCodeProcessSlaRecordMapper;
import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.dp.plat.lowcode.service.ProcessSlaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程 SLA 服务实现（缺口4）。
 *
 * <p><b>双阶段触发逻辑</b>：
 * <ul>
 *   <li>预警阶段：当前时间 ≥ deadline - 20% × SLA 时长（即已用 80% 时间）
 *       且 warning_sent=0 → 调用 slaEscalationMicroflow 微流（context 含 phase=WARNING），
 *       置 warning_sent=1, status=WARNING</li>
 *   <li>升级阶段：当前时间 ≥ deadline 且 escalate_sent=0 → 调用
 *       slaEscalationMicroflow 微流（context 含 phase=ESCALATION），置
 *       escalate_sent=1, status=ESCALATED</li>
 * </ul></p>
 *
 * <p><b>定时任务</b>：{@link #checkSlaStatus} 由 Spring {@code @Scheduled}
 * 每小时执行（cron: 0 0 * * * ?）。{@code @EnableScheduling} 已在
 * {@code PmsApplication} 启用。</p>
 *
 * <p><b>微流触发</b>：通过 {@link MicroflowEngine#execute} 直接调用，
 * context 含 processInstanceId / taskId / phase。微流执行失败仅记 ERROR，
 * 不阻断 SLA 状态更新（best-effort 策略）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessSlaServiceImpl
        extends ServiceImpl<LowCodeProcessSlaRecordMapper, LowCodeProcessSlaRecord>
        implements ProcessSlaService {

    /** 预警阈值：已用 80% 时间（剩余 20%）触发预警 */
    private static final double WARNING_THRESHOLD = 0.8;

    private final LowCodeProcessSlaRecordMapper slaRecordMapper;
    private final LowCodeMicroflowService microflowService;
    private final ObjectMapper objectMapper;

    @Override
    public LowCodeProcessSlaRecord recordSlaForTask(String processInstanceId, String taskId,
                                                     Map<String, Object> slaConfig) {
        if (slaConfig == null || slaConfig.isEmpty()) {
            log.debug("[SLA] SLA 配置为空，跳过记录: taskId={}", taskId);
            return null;
        }
        try {
            LocalDateTime deadline = computeDeadline(slaConfig);
            if (deadline == null) {
                log.warn("[SLA] 无法计算 deadline，跳过记录: taskId={}, config={}", taskId, slaConfig);
                return null;
            }
            // 同一 taskId 已有 ACTIVE 记录则跳过（避免重复创建）
            Long exists = slaRecordMapper.selectCount(new LambdaQueryWrapper<LowCodeProcessSlaRecord>()
                    .eq(LowCodeProcessSlaRecord::getTaskId, taskId)
                    .in(LowCodeProcessSlaRecord::getStatus, List.of("ACTIVE", "WARNING", "ESCALATED")));
            if (exists != null && exists > 0) {
                log.debug("[SLA] 任务已有活跃 SLA 记录，跳过: taskId={}", taskId);
                return null;
            }
            LowCodeProcessSlaRecord record = LowCodeProcessSlaRecord.builder()
                    .processInstanceId(processInstanceId)
                    .taskId(taskId)
                    .slaConfigJson(objectMapper.writeValueAsString(slaConfig))
                    .deadline(deadline)
                    .warningSent(0)
                    .escalateSent(0)
                    .status("ACTIVE")
                    .build();
            slaRecordMapper.insert(record);
            log.info("[SLA] 创建 SLA 记录: taskId={}, deadline={}", taskId, deadline);
            return record;
        } catch (Exception e) {
            log.warn("[SLA] 创建 SLA 记录失败: taskId={}, err={}", taskId, e.getMessage());
            return null;
        }
    }

    @Override
    @Scheduled(cron = "0 0 * * * ?")
    public void checkSlaStatus() {
        List<LowCodeProcessSlaRecord> activeRecords = slaRecordMapper.selectList(
                new LambdaQueryWrapper<LowCodeProcessSlaRecord>()
                        .in(LowCodeProcessSlaRecord::getStatus, List.of("ACTIVE", "WARNING")));
        if (activeRecords.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        log.info("[SLA] 定时检查: 共 {} 条活跃 SLA 记录", activeRecords.size());
        for (LowCodeProcessSlaRecord record : activeRecords) {
            try {
                checkSingleRecord(record, now);
            } catch (Exception e) {
                log.error("[SLA] 检查 SLA 记录失败: id={}, taskId={}", record.getId(), record.getTaskId(), e);
            }
        }
    }

    @Override
    public void completeSla(String taskId) {
        if (taskId == null || taskId.isBlank()) return;
        try {
            List<LowCodeProcessSlaRecord> records = slaRecordMapper.selectList(
                    new LambdaQueryWrapper<LowCodeProcessSlaRecord>()
                            .eq(LowCodeProcessSlaRecord::getTaskId, taskId)
                            .in(LowCodeProcessSlaRecord::getStatus, List.of("ACTIVE", "WARNING", "ESCALATED")));
            for (LowCodeProcessSlaRecord record : records) {
                record.setStatus("COMPLETED");
                slaRecordMapper.updateById(record);
                log.info("[SLA] 任务完成，SLA 记录置 COMPLETED: taskId={}, slaId={}", taskId, record.getId());
            }
        } catch (Exception e) {
            log.warn("[SLA] 完成 SLA 记录失败: taskId={}, err={}", taskId, e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /** 检查单条 SLA 记录的预警/升级状态 */
    private void checkSingleRecord(LowCodeProcessSlaRecord record, LocalDateTime now) {
        Map<String, Object> slaConfig = parseSlaConfig(record.getSlaConfigJson());
        String microflowCode = getString(slaConfig, "slaEscalationMicroflow");
        LocalDateTime deadline = record.getDeadline();
        LocalDateTime createTime = record.getCreateTime() != null ? record.getCreateTime() : deadline.minusHours(1);
        long totalDurationSeconds = java.time.Duration.between(createTime, deadline).getSeconds();
        long elapsedSeconds = java.time.Duration.between(createTime, now).getSeconds();

        // 预警阶段：已用 ≥ 80% 时间 且 未发送预警
        if (record.getWarningSent() == 0
                && totalDurationSeconds > 0
                && ((double) elapsedSeconds / totalDurationSeconds) >= WARNING_THRESHOLD
                && now.isBefore(deadline)) {
            triggerMicroflow(microflowCode, record, "WARNING");
            record.setWarningSent(1);
            record.setStatus("WARNING");
            slaRecordMapper.updateById(record);
            log.info("[SLA] 触发预警: taskId={}, deadline={}", record.getTaskId(), deadline);
        }

        // 升级阶段：当前时间 ≥ deadline 且 未发送升级
        if (record.getEscalateSent() == 0 && !now.isBefore(deadline)) {
            triggerMicroflow(microflowCode, record, "ESCALATION");
            record.setEscalateSent(1);
            record.setStatus("ESCALATED");
            slaRecordMapper.updateById(record);
            log.info("[SLA] 触发升级: taskId={}, deadline={}", record.getTaskId(), deadline);
        }
    }

    /** 触发 SLA 微流（best-effort，失败仅记日志） */
    private void triggerMicroflow(String microflowCode, LowCodeProcessSlaRecord record, String phase) {
        if (microflowCode == null || microflowCode.isBlank()) {
            log.debug("[SLA] 未配置 slaEscalationMicroflow，跳过微流触发: taskId={}, phase={}",
                    record.getTaskId(), phase);
            return;
        }
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("processInstanceId", record.getProcessInstanceId());
            inputs.put("taskId", record.getTaskId());
            inputs.put("phase", phase);
            inputs.put("deadline", record.getDeadline() != null ? record.getDeadline().toString() : null);
            microflowService.execute(microflowCode, inputs);
            log.info("[SLA] 微流触发成功: microflowCode={}, phase={}, taskId={}",
                    microflowCode, phase, record.getTaskId());
        } catch (Exception e) {
            log.error("[SLA] 微流触发失败: microflowCode={}, phase={}, taskId={}, err={}",
                    microflowCode, phase, record.getTaskId(), e.getMessage());
        }
    }

    /** 根据 SLA 配置计算 deadline = createTime + slaDuration slaUnit */
    private LocalDateTime computeDeadline(Map<String, Object> slaConfig) {
        Object durationObj = slaConfig.get("slaDuration");
        String unit = getString(slaConfig, "slaUnit");
        if (durationObj == null) return null;
        long duration;
        try {
            duration = Long.parseLong(durationObj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
        if (duration <= 0) return null;
        LocalDateTime base = LocalDateTime.now();
        if (unit == null || unit.isBlank()) unit = "HOURS";
        return switch (unit.toUpperCase()) {
            case "MINUTES" -> base.plusMinutes(duration);
            case "HOURS" -> base.plusHours(duration);
            case "DAYS" -> base.plusDays(duration);
            default -> base.plusHours(duration);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSlaConfig(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[SLA] 解析 SLA 配置 JSON 失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}
