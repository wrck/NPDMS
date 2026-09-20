package cn.iocoder.yudao.module.pms.service.service.srvexecution;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvexecution.vo.SrvExecutionSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvexecution.SrvExecutionRetiredDO;
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
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Service
@Validated
@Deprecated
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
    public Long createSrvExecutionRetired(SrvExecutionSaveReqVO createReqVO) {
        // 校验任务内编码唯一
        validateCodeUnique(null, createReqVO.getTaskId(), createReqVO.getCode());
        SrvExecutionRetiredDO execution = BeanUtils.toBean(createReqVO, SrvExecutionRetiredDO.class);
        if (execution.getStatus() == null) {
            execution.setStatus(STATUS_PENDING);
        }
        srvExecutionMapper.insert(execution);
        return execution.getId();
    }

    @Override
    public void updateSrvExecutionRetired(SrvExecutionSaveReqVO updateReqVO) {
        SrvExecutionRetiredDO existing = validateSrvExecutionExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getTaskId(), updateReqVO.getCode());
        SrvExecutionRetiredDO updateObj = BeanUtils.toBean(updateReqVO, SrvExecutionRetiredDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        srvExecutionMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvExecutionRetired(Long id) {
        validateSrvExecutionExists(id);
        srvExecutionMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvExecutionRetiredDO> getSrvExecutionPageRetired(SrvExecutionPageReqVO pageReqVO) {
        return srvExecutionMapper.selectPageRetired(pageReqVO);
    }

    @Override
    public SrvExecutionRetiredDO getSrvExecutionRetired(Long id) {
        return srvExecutionMapper.selectById(id);
    }

    @Override
    public void startExecutionRetired(Long id) {
        SrvExecutionRetiredDO execution = validateSrvExecutionExists(id);
        if (!Objects.equals(execution.getStatus(), STATUS_PENDING)) {
            throw exception(SRV_EXECUTION_STATUS_INVALID);
        }
        SrvExecutionRetiredDO updateObj = new SrvExecutionRetiredDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_EXECUTING);
        updateObj.setExecutionTime(LocalDateTime.now());
        srvExecutionMapper.updateById(updateObj);
    }

    @Override
    public void completeExecutionRetired(Long id) {
        SrvExecutionRetiredDO execution = validateSrvExecutionExists(id);
        if (!Objects.equals(execution.getStatus(), STATUS_EXECUTING)) {
            throw exception(SRV_EXECUTION_STATUS_INVALID);
        }
        updateStatus(id, STATUS_COMPLETED);
    }

    @Override
    public void markAbnormalRetired(Long id) {
        SrvExecutionRetiredDO execution = validateSrvExecutionExists(id);
        if (!Objects.equals(execution.getStatus(), STATUS_PENDING)
                && !Objects.equals(execution.getStatus(), STATUS_EXECUTING)) {
            throw exception(SRV_EXECUTION_STATUS_INVALID);
        }
        updateStatus(id, STATUS_ABNORMAL);
    }

    private void updateStatus(Long id, int status) {
        SrvExecutionRetiredDO updateObj = new SrvExecutionRetiredDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        srvExecutionMapper.updateById(updateObj);
    }

    private SrvExecutionRetiredDO validateSrvExecutionExists(Long id) {
        if (id == null) {
            throw exception(SRV_EXECUTION_NOT_EXISTS);
        }
        SrvExecutionRetiredDO execution = srvExecutionMapper.selectById(id);
        if (execution == null) {
            throw exception(SRV_EXECUTION_NOT_EXISTS);
        }
        return execution;
    }

    private void validateCodeUnique(Long id, Long taskId, String code) {
        if (taskId == null || code == null) {
            return;
        }
        SrvExecutionRetiredDO existing = srvExecutionMapper.selectByTaskIdAndCodeRetired(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_EXECUTION_CODE_DUPLICATE, code);
        }
    }

}
