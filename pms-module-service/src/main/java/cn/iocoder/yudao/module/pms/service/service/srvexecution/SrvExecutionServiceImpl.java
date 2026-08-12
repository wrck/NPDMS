package cn.iocoder.yudao.module.pms.service.service.srvexecution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvexecution.SrvExecutionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvexecution.SrvExecutionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_EXECUTION_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_EXECUTION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_EXECUTION_STATUS_INVALID;

/**
 * 巡检执行记录 Service 实现类
 */
@Service
@Validated
public class SrvExecutionServiceImpl implements SrvExecutionService {

    /**
     * 执行状态：0待执行
     */
    private static final int STATUS_PENDING = 0;
    /**
     * 执行状态：1执行中
     */
    private static final int STATUS_EXECUTING = 1;
    /**
     * 执行状态：2已完成
     */
    private static final int STATUS_COMPLETED = 2;
    /**
     * 执行状态：3异常
     */
    private static final int STATUS_ABNORMAL = 3;

    @Resource
    private SrvExecutionMapper srvExecutionMapper;

    @Override
    public Long createSrvExecution(SrvExecutionSaveReqVO createReqVO) {
        // 校验任务内编码唯一
        validateCodeUnique(null, createReqVO.getTaskId(), createReqVO.getCode());
        SrvExecutionDO execution = BeanUtils.toBean(createReqVO, SrvExecutionDO.class);
        if (execution.getStatus() == null) {
            execution.setStatus(STATUS_PENDING);
        }
        srvExecutionMapper.insert(execution);
        return execution.getId();
    }

    @Override
    public void updateSrvExecution(SrvExecutionSaveReqVO updateReqVO) {
        SrvExecutionDO existing = validateSrvExecutionExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getTaskId(), updateReqVO.getCode());
        SrvExecutionDO updateObj = BeanUtils.toBean(updateReqVO, SrvExecutionDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        srvExecutionMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvExecution(Long id) {
        validateSrvExecutionExists(id);
        srvExecutionMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvExecutionDO> getSrvExecutionPage(SrvExecutionPageReqVO pageReqVO) {
        return srvExecutionMapper.selectPage(pageReqVO);
    }

    @Override
    public SrvExecutionDO getSrvExecution(Long id) {
        return srvExecutionMapper.selectById(id);
    }

    @Override
    public void startExecution(Long id) {
        SrvExecutionDO execution = validateSrvExecutionExists(id);
        if (!Objects.equals(execution.getStatus(), STATUS_PENDING)) {
            throw exception(SRV_EXECUTION_STATUS_INVALID);
        }
        SrvExecutionDO updateObj = new SrvExecutionDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_EXECUTING);
        updateObj.setExecutionTime(LocalDateTime.now());
        srvExecutionMapper.updateById(updateObj);
    }

    @Override
    public void completeExecution(Long id) {
        SrvExecutionDO execution = validateSrvExecutionExists(id);
        if (!Objects.equals(execution.getStatus(), STATUS_EXECUTING)) {
            throw exception(SRV_EXECUTION_STATUS_INVALID);
        }
        updateStatus(id, STATUS_COMPLETED);
    }

    @Override
    public void markAbnormal(Long id) {
        SrvExecutionDO execution = validateSrvExecutionExists(id);
        if (!Objects.equals(execution.getStatus(), STATUS_PENDING)
                && !Objects.equals(execution.getStatus(), STATUS_EXECUTING)) {
            throw exception(SRV_EXECUTION_STATUS_INVALID);
        }
        updateStatus(id, STATUS_ABNORMAL);
    }

    private void updateStatus(Long id, int status) {
        SrvExecutionDO updateObj = new SrvExecutionDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        srvExecutionMapper.updateById(updateObj);
    }

    private SrvExecutionDO validateSrvExecutionExists(Long id) {
        if (id == null) {
            throw exception(SRV_EXECUTION_NOT_EXISTS);
        }
        SrvExecutionDO execution = srvExecutionMapper.selectById(id);
        if (execution == null) {
            throw exception(SRV_EXECUTION_NOT_EXISTS);
        }
        return execution;
    }

    private void validateCodeUnique(Long id, Long taskId, String code) {
        if (taskId == null || code == null) {
            return;
        }
        SrvExecutionDO existing = srvExecutionMapper.selectByTaskIdAndCode(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_EXECUTION_CODE_DUPLICATE, code);
        }
    }

}
