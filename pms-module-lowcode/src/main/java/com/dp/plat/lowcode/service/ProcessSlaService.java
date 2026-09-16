package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeProcessSlaRecord;

import java.util.Map;

/**
 * 流程 SLA 服务（缺口4）。
 *
 * <p>实现双阶段 SLA 触发：在任务截止前 80% 时间点触发预警微流，
 * 在截止时间到达后触发升级微流。SLA 配置来自 BPMN 用户任务的
 * {@code lowcode:config} 扩展元素（slaDuration / slaUnit / slaEscalationMicroflow）。</p>
 *
 * <p><b>双阶段阈值</b>：
 * <ul>
 *   <li>预警：当前时间 ≥ deadline - 20% × SLA 时长（即已用 80% 时间）</li>
 *   <li>升级：当前时间 ≥ deadline</li>
 * </ul></p>
 */
public interface ProcessSlaService extends IService<LowCodeProcessSlaRecord> {

    /**
     * 为流程任务创建 SLA 记录。
     *
     * @param processInstanceId 流程实例ID
     * @param taskId            任务ID
     * @param slaConfig         SLA 配置（含 slaDuration / slaUnit / slaEscalationMicroflow）
     * @return 创建的 SLA 记录
     */
    LowCodeProcessSlaRecord recordSlaForTask(String processInstanceId, String taskId, Map<String, Object> slaConfig);

    /**
     * 定时检查所有 ACTIVE 状态的 SLA 记录，触发预警/升级微流。
     *
     * <p>由 Spring {@code @Scheduled} 每小时执行。对每条记录：
     * <ul>
     *   <li>当前时间 ≥ 80% 截止时间 且 warning_sent=0 → 触发预警微流，置 WARNING</li>
     *   <li>当前时间 ≥ deadline 且 escalate_sent=0 → 触发升级微流，置 ESCALATED</li>
     * </ul></p>
     */
    void checkSlaStatus();

    /**
     * 任务完成时调用，将对应 SLA 记录置为 COMPLETED。
     *
     * @param taskId 任务ID
     */
    void completeSla(String taskId);
}
