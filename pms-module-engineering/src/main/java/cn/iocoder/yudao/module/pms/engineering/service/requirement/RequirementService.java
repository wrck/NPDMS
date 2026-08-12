package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementDO;

import jakarta.validation.Valid;

/**
 * PMS 需求分析 Service 接口（FR-ENG-004）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 已生效 → 3 已归档。
 */
public interface RequirementService {

    Long createRequirement(@Valid RequirementSaveReqVO createReqVO);

    void updateRequirement(@Valid RequirementSaveReqVO updateReqVO);

    void deleteRequirement(Long id);

    RequirementDO getRequirement(Long id);

    PageResult<RequirementDO> getRequirementPage(RequirementPageReqVO pageReqVO);

    /**
     * 提交需求：草稿(0) → 已提交(1)
     */
    void submitRequirement(Long id);

    /**
     * 标记生效：已提交(1) → 已生效(2)
     */
    void markEffective(Long id);

    /**
     * 归档需求：已生效(2) → 已归档(3)
     */
    void archiveRequirement(Long id);
}
