package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeTriggerExecutionLog;

import java.util.List;

/**
 * 低代码触发器执行日志服务。
 *
 * <p>提供执行日志的写入与查询能力（按触发器 ID 查询历史 / 查询全局最近执行）。</p>
 */
public interface LowCodeTriggerExecutionLogService extends IService<LowCodeTriggerExecutionLog> {

    /**
     * 记录一次触发器执行结果（成功或失败）。
     *
     * @param log 执行日志
     */
    void record(LowCodeTriggerExecutionLog log);

    /**
     * 按触发器 ID 查询最近执行历史。
     *
     * @param triggerId 触发器 ID
     * @param limit     返回条数上限
     * @return 执行日志列表（按时间倒序）
     */
    List<LowCodeTriggerExecutionLog> listByTriggerId(Long triggerId, int limit);

    /**
     * 查询全局最近执行历史（跨触发器）。
     *
     * @param limit 返回条数上限
     * @return 执行日志列表（按时间倒序）
     */
    List<LowCodeTriggerExecutionLog> listRecent(int limit);

    /**
     * 查询近 N 小时内的全局触发器执行历史（APM 看板用）。
     *
     * @param hours 时间窗口（小时）
     * @param limit 返回条数上限
     * @return 执行日志列表（按创建时间倒序）
     */
    List<LowCodeTriggerExecutionLog> listRecentByHours(int hours, int limit);
}
