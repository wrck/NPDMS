package cn.iocoder.yudao.module.pms.cutover.service.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationRespVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo.CutoverConfigurationValidationRespVO;

public interface CutoverConfigurationService {
    PageResult<CutoverConfigurationRespVO> getPage(CutoverConfigurationPageReqVO request);
    CutoverConfigurationRespVO get(Long revisionId);
    Long create(CutoverConfigurationSaveReqVO request);
    void update(Long revisionId, Long expectedVersion, CutoverConfigurationSaveReqVO request);
    Long copyRevision(Long revisionId, Long expectedVersion);
    CutoverConfigurationValidationRespVO validate(Long revisionId);
    CutoverConfigurationRespVO publish(Long revisionId, Long expectedVersion);
    CutoverConfigurationRespVO disable(Long revisionId, Long expectedVersion);
}
