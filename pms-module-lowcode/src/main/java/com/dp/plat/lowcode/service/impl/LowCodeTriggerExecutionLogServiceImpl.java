package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.entity.LowCodeTriggerExecutionLog;
import com.dp.plat.lowcode.mapper.LowCodeTriggerExecutionLogMapper;
import com.dp.plat.lowcode.service.LowCodeTriggerExecutionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 低代码触发器执行日志服务实现。
 *
 * <p>记录为 best-effort：写入失败仅记日志，不影响主流程。</p>
 */
@Slf4j
@Service
public class LowCodeTriggerExecutionLogServiceImpl
        extends ServiceImpl<LowCodeTriggerExecutionLogMapper, LowCodeTriggerExecutionLog>
        implements LowCodeTriggerExecutionLogService {

    /** 单次查询最大条数上限，避免误传过大 limit 拖慢查询 */
    private static final int MAX_LIMIT = 500;
    /** 默认查询条数 */
    private static final int DEFAULT_LIMIT = 50;

    @Override
    public void record(LowCodeTriggerExecutionLog executionLog) {
        try {
            save(executionLog);
        } catch (Exception e) {
            // 日志写入失败不阻断主流程
            log.error("记录触发器执行日志失败: triggerCode={}", executionLog.getTriggerCode(), e);
        }
    }

    @Override
    public List<LowCodeTriggerExecutionLog> listByTriggerId(Long triggerId, int limit) {
        int safeLimit = sanitizeLimit(limit);
        return list(new LambdaQueryWrapper<LowCodeTriggerExecutionLog>()
                .eq(LowCodeTriggerExecutionLog::getTriggerId, triggerId)
                .orderByDesc(LowCodeTriggerExecutionLog::getId)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public List<LowCodeTriggerExecutionLog> listRecent(int limit) {
        int safeLimit = sanitizeLimit(limit);
        return list(new LambdaQueryWrapper<LowCodeTriggerExecutionLog>()
                .orderByDesc(LowCodeTriggerExecutionLog::getId)
                .last("LIMIT " + safeLimit));
    }

    @Override
    public List<LowCodeTriggerExecutionLog> listRecentByHours(int hours, int limit) {
        int safeLimit = sanitizeLimit(limit);
        int safeHours = hours <= 0 ? 24 : hours;
        return list(new LambdaQueryWrapper<LowCodeTriggerExecutionLog>()
                .ge(LowCodeTriggerExecutionLog::getCreateTime,
                        LocalDateTime.now().minusHours(safeHours))
                .orderByDesc(LowCodeTriggerExecutionLog::getCreateTime)
                .last("LIMIT " + safeLimit));
    }

    /** 规范化 limit：非正数或超过上限时回落到默认值 */
    private int sanitizeLimit(int limit) {
        if (limit <= 0 || limit > MAX_LIMIT) {
            return DEFAULT_LIMIT;
        }
        return limit;
    }
}
