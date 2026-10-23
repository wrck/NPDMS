package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.business.configuration.vo.CutoverConfigurationValidationRespVO;

public interface CutoverConfigurationService {
    PageResult<CutoverConfigurationRespVO> getPage(CutoverConfigurationPageReqVO request);
    CutoverConfigurationRespVO get(Long revisionId);
    Long create(CutoverConfigurationCommands.Create command);
    void update(CutoverConfigurationCommands.Update command);
    Long copyRevision(CutoverConfigurationCommands.RevisionAction command);
    CutoverConfigurationValidationRespVO validate(Long revisionId);
    CutoverConfigurationRespVO publish(CutoverConfigurationCommands.RevisionAction command);
    CutoverConfigurationRespVO disable(CutoverConfigurationCommands.RevisionAction command);
}
