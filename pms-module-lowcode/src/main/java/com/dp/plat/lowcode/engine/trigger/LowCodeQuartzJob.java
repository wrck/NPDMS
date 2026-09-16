package com.dp.plat.lowcode.engine.trigger;

import com.dp.plat.lowcode.service.LowCodeTriggerService;
import com.dp.plat.lowcode.util.SpringApplicationContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

import java.util.HashMap;

/**
 * 低代码 Quartz 定时任务。
 *
 * <p>由 Quartz 调度器按 cron 表达式触发，从 JobDataMap 取出 triggerCode，
 * 通过 {@link SpringApplicationContextHolder} 获取 {@link LowCodeTriggerService}
 * 并执行对应触发器（最终调用目标微流）。</p>
 *
 * <p>异常捕获后仅记录日志，不向上抛出，避免 Quartz 反复触发失败任务。</p>
 */
@Slf4j
public class LowCodeQuartzJob implements Job {

    public static final String DATA_KEY_TRIGGER_CODE = "triggerCode";
    public static final String JOB_GROUP = "lowcode";

    @Override
    public void execute(JobExecutionContext context) {
        String triggerCode = context.getMergedJobDataMap().getString(DATA_KEY_TRIGGER_CODE);
        if (triggerCode == null || triggerCode.isBlank()) {
            log.warn("Quartz 任务缺少 triggerCode: job={}", context.getJobDetail().getKey());
            return;
        }
        try {
            LowCodeTriggerService triggerService = SpringApplicationContextHolder.getBean(LowCodeTriggerService.class);
            triggerService.executeTrigger(triggerCode, new HashMap<>());
            log.debug("Quartz 触发器执行完成: triggerCode={}", triggerCode);
        } catch (Exception e) {
            // 不重抛，避免 Quartz 反复触发失败任务
            log.error("Quartz 触发器执行失败: triggerCode={}", triggerCode, e);
        }
    }
}
