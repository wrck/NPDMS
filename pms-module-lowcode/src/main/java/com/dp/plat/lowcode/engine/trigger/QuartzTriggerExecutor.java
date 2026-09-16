package com.dp.plat.lowcode.engine.trigger;

import com.dp.plat.lowcode.service.LowCodeMicroflowService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Quartz 定时触发器执行器。
 *
 * <p>QUARTZ 触发器 config JSON 约定：{cronExpression:"0 0/5 * * * ?"}，
 * 兼容旧字段 {cron:"0 0/5 * * * ?"}。</p>
 *
 * <p>真实调度：通过 {@link Scheduler} 注册 {@link LowCodeQuartzJob}，按 cron 表达式周期触发。
 * 手动执行（{@link #execute}）保持原有直接调用目标微流的能力。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzTriggerExecutor implements TriggerExecutor {

    private final LowCodeMicroflowService microflowService;
    private final ObjectMapper objectMapper;
    private final Scheduler scheduler;

    @Override
    public String supportedType() {
        return "QUARTZ";
    }

    @Override
    public Map<String, Object> execute(LowCodeTrigger trigger, Map<String, Object> data) {
        log.info("Quartz 触发器手动执行: trigger={}, cron config={}", trigger.getCode(), trigger.getConfig());
        if ("MICROFLOW".equals(trigger.getTargetType())) {
            return microflowService.execute(trigger.getTargetCode(), data);
        }
        return Map.of("message", "Quartz trigger fired, target=" + trigger.getTargetCode());
    }

    /**
     * 注册 / 更新 Quartz 调度任务。
     *
     * <p>若同名任务已存在则先移除再注册（覆盖式更新），保证 cron 变更后生效。</p>
     *
     * @param trigger 低代码触发器
     */
    public void scheduleJob(LowCodeTrigger trigger) {
        String cronExpression = parseCronExpression(trigger.getConfig());
        if (cronExpression == null || cronExpression.isBlank()) {
            log.warn("QUARTZ 触发器缺少 cronExpression，跳过调度: {}", trigger.getCode());
            return;
        }
        try {
            JobKey jobKey = jobKey(trigger.getCode());
            // 覆盖式更新：已存在则先删除
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }

            JobDetail jobDetail = JobBuilder.newJob(LowCodeQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(LowCodeQuartzJob.DATA_KEY_TRIGGER_CODE, trigger.getCode())
                    .storeDurably()
                    .build();

            CronTrigger cronTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(trigger.getCode()))
                    .forJob(jobDetail)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build();

            scheduler.scheduleJob(jobDetail, cronTrigger);
            log.info("已注册 Quartz 调度任务: triggerCode={}, cron={}", trigger.getCode(), cronExpression);
        } catch (SchedulerException e) {
            log.error("注册 Quartz 调度任务失败: triggerCode={}, cron={}", trigger.getCode(), cronExpression, e);
            throw new RuntimeException("注册 Quartz 调度任务失败: " + trigger.getCode(), e);
        } catch (Exception e) {
            // cron 表达式非法等
            log.error("注册 Quartz 调度任务失败（表达式非法?）: triggerCode={}, cron={}", trigger.getCode(), cronExpression, e);
            throw new RuntimeException("注册 Quartz 调度任务失败: " + trigger.getCode(), e);
        }
    }

    /**
     * 按 triggerCode 卸载 Quartz 调度任务。
     *
     * @param triggerCode 触发器编码
     */
    public void unscheduleJob(String triggerCode) {
        try {
            JobKey jobKey = jobKey(triggerCode);
            if (scheduler.checkExists(jobKey)) {
                boolean deleted = scheduler.deleteJob(jobKey);
                log.info("卸载 Quartz 调度任务: triggerCode={}, deleted={}", triggerCode, deleted);
            } else {
                // 兼容：按 triggerKey 直接取消（理论上 deleteJob 已覆盖）
                scheduler.unscheduleJob(triggerKey(triggerCode));
            }
        } catch (SchedulerException e) {
            log.error("卸载 Quartz 调度任务失败: triggerCode={}", triggerCode, e);
        }
    }

    private JobKey jobKey(String triggerCode) {
        return new JobKey(triggerCode, LowCodeQuartzJob.JOB_GROUP);
    }

    private TriggerKey triggerKey(String triggerCode) {
        return new TriggerKey(triggerCode + "-trigger", LowCodeQuartzJob.JOB_GROUP);
    }

    @SuppressWarnings("unchecked")
    private String parseCronExpression(String config) {
        if (config == null || config.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> cfg = objectMapper.readValue(config, new TypeReference<>() {});
            Object cron = cfg.get("cronExpression");
            if (cron == null) {
                cron = cfg.get("cron"); // 兼容旧字段
            }
            return cron == null ? null : cron.toString();
        } catch (Exception e) {
            log.warn("解析 QUARTZ 触发器 config 失败: {}", config, e);
            return null;
        }
    }
}
