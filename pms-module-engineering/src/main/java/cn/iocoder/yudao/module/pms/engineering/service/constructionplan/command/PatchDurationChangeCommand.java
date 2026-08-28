package cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command;

import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.patch.DurationChangePatch;

public record PatchDurationChangeCommand(
        Long planId, Long changeId, Integer expectedChangeVersion, Integer expectedProjectVersion,
        DurationChangePatch patch) {
}
