package cn.iocoder.yudao.module.pms.cutover.service.plan.result;

import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;

public record DownloadCutoverPlanDraftResult(Long planRevisionId, Integer planVersion,
                                             CutoverPlanFilePort.FileFact fileArtifactFact,
                                             Long downloadedAt) {
}
