package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.service.support.CutoverWriteIntent;

public final class CutoverConfigurationCommands {
    private CutoverConfigurationCommands() { }
    public record Create(CutoverConfigurationSaveReqVO content, CutoverWriteIntent intent) { }
    public record Update(Long revisionId, Long expectedVersion, CutoverConfigurationSaveReqVO content,
                         CutoverWriteIntent intent) { }
    public record RevisionAction(Long revisionId, Long expectedVersion, CutoverWriteIntent intent) { }
}
