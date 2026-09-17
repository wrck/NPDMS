package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.entity.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.entity.BriefingEntityDO;
import jakarta.validation.Valid;

/** 独立交底业务契约，完整承接原查询、编辑和生命周期操作。 */
public interface BriefingEntityService {
    Long createBriefing(@Valid BriefingEntitySaveReqVO request);
    void updateBriefing(@Valid BriefingEntitySaveReqVO request);
    void deleteBriefing(Long id);
    BriefingEntityDO getBriefing(Long id);
    BriefingEntityDO validateBriefingExists(Long id);
    PageResult<BriefingEntityDO> getBriefingPage(BriefingEntityPageReqVO request);
    void generateBriefing(@Valid BriefingEntityGenerateReqVO request);
    void approveBriefing(@Valid BriefingEntityApproveReqVO request);
    void publishBriefing(Long id);
    void terminateBriefing(Long id);
}
