package cn.iocoder.yudao.module.pms.engineering.service.preparation.command;

import cn.iocoder.yudao.module.pms.engineering.api.readiness.dto.SiteSurveyReadinessFact;

public record PreparationReadinessResult(SiteSurveyReadinessFact readiness, boolean replayed) {}
