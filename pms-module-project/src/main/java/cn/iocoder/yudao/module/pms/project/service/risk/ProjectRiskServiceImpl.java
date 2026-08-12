package cn.iocoder.yudao.module.pms.project.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.risk.vo.ProjectRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.risk.ProjectRiskDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.risk.ProjectRiskMapper;
import cn.iocoder.yudao.module.pms.project.domain.risk.RiskStatusRules;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_RISK_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_RISK_PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_RISK_STATUS_TRANSITION_INVALID;

/**
 * PMS 项目风险 Service 实现（FR-PROJ-026 / T-V1-PROJ-009）。
 * <p>
 * 风险状态机：已识别→处理中→已关闭/已发生；已发生→已关闭。
 * 创建时默认状态为已识别并写入 identified_at；迁入已关闭时写入 closed_at。
 */
@Service
@Validated
@Slf4j
public class ProjectRiskServiceImpl implements ProjectRiskService {

    /** 合法风险等级集合 */
    private static final Set<String> VALID_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");

    @Resource
    private ProjectRiskMapper projectRiskMapper;
    @Resource
    private ProjectMapper projectMapper;

    @Override
    public Long createRisk(ProjectRiskSaveReqVO createReqVO) {
        // 1. 校验项目存在
        if (projectMapper.selectById(createReqVO.getProjectId()) == null) {
            throw exception(PROJECT_RISK_PROJECT_NOT_EXISTS);
        }
        // 2. 校验风险等级合法
        validateRiskLevel(createReqVO.getRiskLevel());
        // 3. 写入
        ProjectRiskDO risk = BeanUtils.toBean(createReqVO, ProjectRiskDO.class);
        if (risk.getStatus() == null) {
            risk.setStatus(RiskStatusRules.IDENTIFIED);
        }
        if (risk.getIdentifiedAt() == null) {
            risk.setIdentifiedAt(LocalDateTime.now());
        }
        projectRiskMapper.insert(risk);
        return risk.getId();
    }

    @Override
    public void updateRisk(ProjectRiskSaveReqVO updateReqVO) {
        // 1. 校验存在
        ProjectRiskDO existing = validateRiskExists(updateReqVO.getId());
        // 2. 项目不可变
        if (!Objects.equals(existing.getProjectId(), updateReqVO.getProjectId())) {
            throw exception(PROJECT_RISK_NOT_EXISTS);
        }
        // 3. 校验风险等级合法
        validateRiskLevel(updateReqVO.getRiskLevel());
        // 4. 校验状态迁移合法性（若状态变更）
        if (updateReqVO.getStatus() != null && !Objects.equals(existing.getStatus(), updateReqVO.getStatus())) {
            int from = existing.getStatus() != null ? existing.getStatus() : RiskStatusRules.IDENTIFIED;
            if (!RiskStatusRules.canTransition(from, updateReqVO.getStatus())) {
                throw exception(PROJECT_RISK_STATUS_TRANSITION_INVALID);
            }
        }
        // 5. 更新（乐观锁由 @Version 自动处理）
        ProjectRiskDO update = BeanUtils.toBean(updateReqVO, ProjectRiskDO.class);
        projectRiskMapper.updateById(update);
    }

    @Override
    public void deleteRisk(Long id) {
        // 1. 校验存在
        validateRiskExists(id);
        // 2. 删除
        projectRiskMapper.deleteById(id);
    }

    @Override
    public void deleteRiskList(Collection<Long> ids) {
        for (Long id : ids) {
            deleteRisk(id);
        }
    }

    @Override
    public ProjectRiskDO getRisk(Long id) {
        return projectRiskMapper.selectById(id);
    }

    @Override
    public ProjectRiskDO validateRiskExists(Long id) {
        ProjectRiskDO risk = projectRiskMapper.selectById(id);
        if (risk == null) {
            throw exception(PROJECT_RISK_NOT_EXISTS);
        }
        return risk;
    }

    @Override
    public PageResult<ProjectRiskDO> getRiskPage(ProjectRiskPageReqVO pageReqVO) {
        return projectRiskMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ProjectRiskDO> getRiskListByProjectId(Long projectId) {
        return projectRiskMapper.selectListByProjectId(projectId);
    }

    @Override
    public void transitionStatus(Long riskId, int targetStatus, Integer version) {
        // 1. 校验存在
        ProjectRiskDO existing = validateRiskExists(riskId);
        // 2. 校验状态迁移合法性
        int from = existing.getStatus() != null ? existing.getStatus() : RiskStatusRules.IDENTIFIED;
        if (!RiskStatusRules.canTransition(from, targetStatus)) {
            throw exception(PROJECT_RISK_STATUS_TRANSITION_INVALID);
        }
        // 3. 写入新状态、版本号；迁入已关闭时写入 closed_at
        ProjectRiskDO update = new ProjectRiskDO();
        update.setId(riskId);
        update.setStatus(targetStatus);
        update.setVersion(version);
        if (RiskStatusRules.isClosed(targetStatus)) {
            update.setClosedAt(LocalDateTime.now());
        }
        projectRiskMapper.updateById(update);
    }

    // ==================== 内部工具方法 ====================

    private void validateRiskLevel(String riskLevel) {
        if (StringUtils.isBlank(riskLevel) || !VALID_LEVELS.contains(riskLevel)) {
            throw exception(PROJECT_RISK_STATUS_TRANSITION_INVALID);
        }
    }
}
