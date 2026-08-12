package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.vo.RequirementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 需求分析 Service 实现（FR-ENG-004）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 已生效 → 3 已归档。
 */
@Service
@Validated
public class RequirementServiceImpl implements RequirementService {

    @Resource
    private RequirementMapper requirementMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRequirement(RequirementSaveReqVO createReqVO) {
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode(), null);
        RequirementDO requirement = BeanUtils.toBean(createReqVO, RequirementDO.class);
        if (requirement.getStatus() == null) {
            requirement.setStatus(0); // 草稿
        }
        if (requirement.getVersion() == null) {
            requirement.setVersion(0);
        }
        requirementMapper.insert(requirement);
        return requirement.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRequirement(RequirementSaveReqVO updateReqVO) {
        RequirementDO existing = validateRequirementExists(updateReqVO.getId());
        validateCodeUnique(existing.getProjectId(), updateReqVO.getCode(), updateReqVO.getId());
        validateVersion(existing, updateReqVO.getVersion());
        RequirementDO update = BeanUtils.toBean(updateReqVO, RequirementDO.class);
        requirementMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRequirement(Long id) {
        validateRequirementExists(id);
        requirementMapper.deleteById(id);
    }

    @Override
    public RequirementDO getRequirement(Long id) {
        return requirementMapper.selectById(id);
    }

    @Override
    public PageResult<RequirementDO> getRequirementPage(RequirementPageReqVO pageReqVO) {
        return requirementMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitRequirement(Long id) {
        RequirementDO requirement = validateRequirementExists(id);
        validateStatus(requirement, 0); // 草稿 → 已提交
        updateStatus(requirement, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markEffective(Long id) {
        RequirementDO requirement = validateRequirementExists(id);
        validateStatus(requirement, 1); // 已提交 → 已生效
        updateStatus(requirement, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveRequirement(Long id) {
        RequirementDO requirement = validateRequirementExists(id);
        validateStatus(requirement, 2); // 已生效 → 已归档
        updateStatus(requirement, 3);
    }

    // ==================== 内部工具方法 ====================

    private RequirementDO validateRequirementExists(Long id) {
        RequirementDO requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw exception(REQUIREMENT_NOT_EXISTS);
        }
        return requirement;
    }

    private void validateCodeUnique(Long projectId, String code, Long excludeId) {
        RequirementDO existing = requirementMapper.selectByProjectIdAndCode(projectId, code);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(REQUIREMENT_CODE_DUPLICATE);
        }
    }

    private void validateVersion(RequirementDO requirement, Integer version) {
        if (version != null && !Objects.equals(requirement.getVersion(), version)) {
            throw exception(REQUIREMENT_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(RequirementDO requirement, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(requirement.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(REQUIREMENT_STATUS_INVALID);
    }

    private void updateStatus(RequirementDO requirement, int newStatus) {
        requirement.setStatus(newStatus);
        requirement.setVersion(requirement.getVersion() + 1);
        requirementMapper.updateById(requirement);
    }
}
