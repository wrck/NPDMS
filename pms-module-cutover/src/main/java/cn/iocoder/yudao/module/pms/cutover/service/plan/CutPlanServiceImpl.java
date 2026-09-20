package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanApproveReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.plan.vo.CutPlanSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutPlanRetiredDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.CutPlanMapper;
import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.*;

/**
 * PMS 割接方案 Service 实现（FR-CUT-008 / FR-CUT-009）。
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Service
@Validated
@Slf4j
@Deprecated
public class CutPlanServiceImpl implements CutPlanService {

    @Resource
    private CutPlanMapper cutPlanMapper;

    @Override
    public Long createCutPlanRetired(CutPlanSaveReqVO createReqVO) {
        // 1. 校验编码在任务内唯一
        validateCodeUniqueInTask(null, createReqVO.getTaskId(), createReqVO.getCode());
        // 2. 转换并写入，初始状态为草稿
        CutPlanRetiredDO entity = BeanUtils.toBean(createReqVO, CutPlanRetiredDO.class);
        entity.setStatus(CutStatusEnum.CUT_PLAN_DRAFT);
        cutPlanMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateCutPlanRetired(CutPlanSaveReqVO updateReqVO) {
        // 1. 校验存在
        CutPlanRetiredDO existing = validateCutPlanExistsRetired(updateReqVO.getId());
        // 2. 编码不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(CUT_PLAN_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 3. 终态方案不允许修改
        if (isTerminal(existing.getStatus())) {
            throw exception(CUT_PLAN_STATUS_INVALID);
        }
        // 4. 基线锁定校验：已通过方案修改关键字段需重新提交评审
        if (Objects.equals(CutStatusEnum.CUT_PLAN_APPROVED, existing.getStatus())
                && isBaselineFieldsChanged(existing, updateReqVO)) {
            throw exception(CUT_PLAN_BASELINE_LOCKED);
        }
        // 5. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        CutPlanRetiredDO update = BeanUtils.toBean(updateReqVO, CutPlanRetiredDO.class);
        cutPlanMapper.updateById(update);
    }

    @Override
    public void deleteCutPlanRetired(Long id) {
        validateCutPlanExistsRetired(id);
        cutPlanMapper.deleteById(id);
    }

    @Override
    public CutPlanRetiredDO getCutPlanRetired(Long id) {
        return cutPlanMapper.selectById(id);
    }

    @Override
    public CutPlanRetiredDO validateCutPlanExistsRetired(Long id) {
        CutPlanRetiredDO entity = cutPlanMapper.selectById(id);
        if (entity == null) {
            throw exception(CUT_PLAN_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public PageResult<CutPlanRetiredDO> getCutPlanPageRetired(CutPlanPageReqVO pageReqVO) {
        return cutPlanMapper.selectPageRetired(pageReqVO);
    }

    @Override
    public List<CutPlanRetiredDO> getCutPlanListByTaskRetired(Long taskId) {
        return cutPlanMapper.selectListByTaskRetired(taskId);
    }

    @Override
    public void submitForReviewRetired(Long id) {
        CutPlanRetiredDO entity = validateCutPlanExistsRetired(id);
        if (!Objects.equals(CutStatusEnum.CUT_PLAN_DRAFT, entity.getStatus())) {
            throw exception(CUT_PLAN_STATUS_INVALID);
        }
        CutPlanRetiredDO update = new CutPlanRetiredDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_PLAN_PENDING_REVIEW);
        update.setVersion(entity.getVersion());
        cutPlanMapper.updateById(update);
    }

    @Override
    public void approveRetired(CutPlanApproveReqVO reqVO) {
        CutPlanRetiredDO entity = validateCutPlanExistsRetired(reqVO.getId());
        if (!Objects.equals(CutStatusEnum.CUT_PLAN_PENDING_REVIEW, entity.getStatus())) {
            throw exception(CUT_PLAN_STATUS_INVALID);
        }
        CutPlanRetiredDO update = new CutPlanRetiredDO();
        update.setId(reqVO.getId());
        update.setStatus(CutStatusEnum.CUT_PLAN_APPROVED);
        update.setApprovedBy(getLoginUserId());
        update.setApprovedTime(LocalDateTime.now());
        update.setApprovalOpinion(reqVO.getApprovalOpinion());
        // 审核通过后将当前 version 写入 baseline_version 形成不可覆盖基线
        update.setBaselineVersion(entity.getVersion());
        update.setVersion(reqVO.getVersion() != null ? reqVO.getVersion() : entity.getVersion());
        cutPlanMapper.updateById(update);
    }

    @Override
    public void rejectRetired(CutPlanApproveReqVO reqVO) {
        CutPlanRetiredDO entity = validateCutPlanExistsRetired(reqVO.getId());
        if (!Objects.equals(CutStatusEnum.CUT_PLAN_PENDING_REVIEW, entity.getStatus())) {
            throw exception(CUT_PLAN_STATUS_INVALID);
        }
        CutPlanRetiredDO update = new CutPlanRetiredDO();
        update.setId(reqVO.getId());
        update.setStatus(CutStatusEnum.CUT_PLAN_REJECTED);
        update.setApprovedBy(getLoginUserId());
        update.setApprovedTime(LocalDateTime.now());
        update.setApprovalOpinion(reqVO.getApprovalOpinion());
        update.setVersion(reqVO.getVersion() != null ? reqVO.getVersion() : entity.getVersion());
        cutPlanMapper.updateById(update);
    }

    @Override
    public void terminateRetired(Long id) {
        CutPlanRetiredDO entity = validateCutPlanExistsRetired(id);
        if (isTerminal(entity.getStatus())) {
            throw exception(CUT_PLAN_STATUS_INVALID);
        }
        CutPlanRetiredDO update = new CutPlanRetiredDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_PLAN_TERMINATED);
        update.setVersion(entity.getVersion());
        cutPlanMapper.updateById(update);
    }

    @Override
    public void validateTaskPlanApprovedRetired(Long taskId) {
        Long count = cutPlanMapper.selectCountByTaskApprovedRetired(taskId);
        if (count == null || count <= 0) {
            throw exception(CUT_TASK_NOT_APPROVED, taskId);
        }
    }

    private boolean isTerminal(Integer status) {
        return Objects.equals(CutStatusEnum.CUT_PLAN_APPROVED, status)
                || Objects.equals(CutStatusEnum.CUT_PLAN_REJECTED, status)
                || Objects.equals(CutStatusEnum.CUT_PLAN_TERMINATED, status);
    }

    /**
     * 判断基线锁定关键字段是否变更：preCheck / procedure / rollback。
     */
    private boolean isBaselineFieldsChanged(CutPlanRetiredDO existing, CutPlanSaveReqVO updateReqVO) {
        return !Objects.equals(existing.getPreCheck(), updateReqVO.getPreCheck())
                || !Objects.equals(existing.getProcedure(), updateReqVO.getProcedure())
                || !Objects.equals(existing.getRollback(), updateReqVO.getRollback());
    }

    private void validateCodeUniqueInTask(Long id, Long taskId, String code) {
        if (StringUtils.isBlank(code) || taskId == null) {
            return;
        }
        CutPlanRetiredDO existing = cutPlanMapper.selectByTaskCodeRetired(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(CUT_PLAN_CODE_DUPLICATE, code);
        }
    }
}
