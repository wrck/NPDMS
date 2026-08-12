package cn.iocoder.yudao.module.pms.cutover.service.execution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo.CutExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.execution.vo.CutExecutionSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.execution.CutExecutionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.execution.CutExecutionMapper;
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
 * PMS 割接执行 Service 实现（FR-CUT-011 / FR-CUT-012）。
 */
@Service
@Validated
@Slf4j
public class CutExecutionServiceImpl implements CutExecutionService {

    @Resource
    private CutExecutionMapper cutExecutionMapper;

    @Override
    public Long createCutExecution(CutExecutionSaveReqVO createReqVO) {
        // 1. 校验编码在任务内唯一
        validateCodeUniqueInTask(null, createReqVO.getTaskId(), createReqVO.getCode());
        // 2. 转换并写入，初始状态为待执行
        CutExecutionDO entity = BeanUtils.toBean(createReqVO, CutExecutionDO.class);
        entity.setStatus(CutStatusEnum.CUT_EXECUTION_PENDING);
        cutExecutionMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateCutExecution(CutExecutionSaveReqVO updateReqVO) {
        // 1. 校验存在
        CutExecutionDO existing = validateCutExecutionExists(updateReqVO.getId());
        // 2. 编码不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(CUT_EXECUTION_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 3. 终态不允许修改
        if (isTerminal(existing.getStatus())) {
            throw exception(CUT_EXECUTION_STATUS_INVALID);
        }
        // 4. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        CutExecutionDO update = BeanUtils.toBean(updateReqVO, CutExecutionDO.class);
        cutExecutionMapper.updateById(update);
    }

    @Override
    public void deleteCutExecution(Long id) {
        validateCutExecutionExists(id);
        cutExecutionMapper.deleteById(id);
    }

    @Override
    public CutExecutionDO getCutExecution(Long id) {
        return cutExecutionMapper.selectById(id);
    }

    @Override
    public CutExecutionDO validateCutExecutionExists(Long id) {
        CutExecutionDO entity = cutExecutionMapper.selectById(id);
        if (entity == null) {
            throw exception(CUT_EXECUTION_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public PageResult<CutExecutionDO> getCutExecutionPage(CutExecutionPageReqVO pageReqVO) {
        return cutExecutionMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CutExecutionDO> getCutExecutionListByTask(Long taskId) {
        return cutExecutionMapper.selectListByTask(taskId);
    }

    @Override
    public void start(Long id) {
        CutExecutionDO entity = validateCutExecutionExists(id);
        if (!Objects.equals(CutStatusEnum.CUT_EXECUTION_PENDING, entity.getStatus())) {
            throw exception(CUT_EXECUTION_STATUS_INVALID);
        }
        CutExecutionDO update = new CutExecutionDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_EXECUTION_EXECUTING);
        update.setOperatorUserId(getLoginUserId());
        update.setOperationTime(LocalDateTime.now());
        update.setVersion(entity.getVersion());
        cutExecutionMapper.updateById(update);
    }

    @Override
    public void pass(Long id) {
        CutExecutionDO entity = validateCutExecutionExists(id);
        if (!Objects.equals(CutStatusEnum.CUT_EXECUTION_EXECUTING, entity.getStatus())) {
            throw exception(CUT_EXECUTION_STATUS_INVALID);
        }
        CutExecutionDO update = new CutExecutionDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_EXECUTION_PASSED);
        update.setVersion(entity.getVersion());
        cutExecutionMapper.updateById(update);
    }

    @Override
    public void fail(Long id) {
        CutExecutionDO entity = validateCutExecutionExists(id);
        if (!Objects.equals(CutStatusEnum.CUT_EXECUTION_EXECUTING, entity.getStatus())) {
            throw exception(CUT_EXECUTION_STATUS_INVALID);
        }
        CutExecutionDO update = new CutExecutionDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_EXECUTION_FAILED);
        update.setVersion(entity.getVersion());
        cutExecutionMapper.updateById(update);
    }

    @Override
    public void rollback(Long id) {
        CutExecutionDO entity = validateCutExecutionExists(id);
        if (!Objects.equals(CutStatusEnum.CUT_EXECUTION_EXECUTING, entity.getStatus())) {
            throw exception(CUT_EXECUTION_STATUS_INVALID);
        }
        CutExecutionDO update = new CutExecutionDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_EXECUTION_ROLLBACK);
        update.setVersion(entity.getVersion());
        cutExecutionMapper.updateById(update);
    }

    private boolean isTerminal(Integer status) {
        return Objects.equals(CutStatusEnum.CUT_EXECUTION_PASSED, status)
                || Objects.equals(CutStatusEnum.CUT_EXECUTION_FAILED, status)
                || Objects.equals(CutStatusEnum.CUT_EXECUTION_ROLLBACK, status);
    }

    private void validateCodeUniqueInTask(Long id, Long taskId, String code) {
        if (StringUtils.isBlank(code) || taskId == null) {
            return;
        }
        CutExecutionDO existing = cutExecutionMapper.selectByTaskCode(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(CUT_EXECUTION_CODE_DUPLICATE, code);
        }
    }
}
