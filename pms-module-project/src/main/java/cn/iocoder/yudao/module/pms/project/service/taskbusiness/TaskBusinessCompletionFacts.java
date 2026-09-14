package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

/** An actual Owner handling result is required in addition to any configured condition. */
public record TaskBusinessCompletionFacts(TaskBusinessLinkedFacts facts, boolean hasCompletedHandling) { }
