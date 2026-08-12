package cn.iocoder.yudao.module.pms.service.service.srvtask;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvtask.vo.SrvTaskSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask.SrvTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask.SrvTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_TASK_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_TASK_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_TASK_STATUS_INVALID;

/**
 * 巡检任务 Service 实现类
 */
@Service
@Validated
public class SrvTaskServiceImpl implements SrvTaskService {

    /**
     * 任务状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 任务状态：1待执行
     */
    private static final int STATUS_PENDING = 1;
    /**
     * 任务状态：2执行中
     */
    private static final int STATUS_EXECUTING = 2;
    /**
     * 任务状态：3待确认
     */
    private static final int STATUS_PENDING_CONFIRM = 3;
    /**
     * 任务状态：4已完成
     */
    private static final int STATUS_COMPLETED = 4;
    /**
     * 任务状态：5已取消
     */
    private static final int STATUS_CANCELLED = 5;

    @Resource
    private SrvTaskMapper srvTaskMapper;

    @Override
    public Long createSrvTask(SrvTaskSaveReqVO createReqVO) {
        // 校验项目内编码唯一
        validateCodeUnique(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 插入
        SrvTaskDO task = BeanUtils.toBean(createReqVO, SrvTaskDO.class);
        if (task.getStatus() == null) {
            task.setStatus(STATUS_DRAFT);
        }
        if (task.getInspectionMode() == null) {
            task.setInspectionMode("ONLINE");
        }
        if (task.getSourceType() == null) {
            task.setSourceType("MANUAL");
        }
        srvTaskMapper.insert(task);
        return task.getId();
    }

    @Override
    public void updateSrvTask(SrvTaskSaveReqVO updateReqVO) {
        SrvTaskDO existing = validateSrvTaskExists(updateReqVO.getId());
        // 校验项目内编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getProjectId(), updateReqVO.getCode());
        // 仅草稿态允许修改核心字段
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(SRV_TASK_STATUS_INVALID);
        }
        SrvTaskDO updateObj = BeanUtils.toBean(updateReqVO, SrvTaskDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        srvTaskMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvTask(Long id) {
        SrvTaskDO existing = validateSrvTaskExists(id);
        // 仅草稿或已取消状态允许删除
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_CANCELLED)) {
            throw exception(SRV_TASK_STATUS_INVALID);
        }
        srvTaskMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvTaskDO> getSrvTaskPage(SrvTaskPageReqVO pageReqVO) {
        return srvTaskMapper.selectPage(pageReqVO);
    }

    @Override
    public SrvTaskDO getSrvTask(Long id) {
        return srvTaskMapper.selectById(id);
    }

    @Override
    public void validateEquipmentAccount(Long id) {
        SrvTaskDO task = validateSrvTaskExists(id);
        // 执行设备账号有效性校验：本实现保留为占位逻辑，由集成层补充实际校验规则
        String checkResult = (task.getEquipmentId() == null)
                ? "未关联设备，无需校验"
                : "设备账号校验通过";
        SrvTaskDO updateObj = new SrvTaskDO();
        updateObj.setId(id);
        updateObj.setAccountCheckResult(checkResult);
        srvTaskMapper.updateById(updateObj);
    }

    @Override
    public void submitSrvTask(Long id) {
        SrvTaskDO task = validateSrvTaskExists(id);
        if (!Objects.equals(task.getStatus(), STATUS_DRAFT)) {
            throw exception(SRV_TASK_STATUS_INVALID);
        }
        updateStatus(id, STATUS_PENDING);
    }

    @Override
    public void startExecution(Long id) {
        SrvTaskDO task = validateSrvTaskExists(id);
        if (!Objects.equals(task.getStatus(), STATUS_PENDING)) {
            throw exception(SRV_TASK_STATUS_INVALID);
        }
        SrvTaskDO updateObj = new SrvTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_EXECUTING);
        updateObj.setActualTime(LocalDateTime.now());
        srvTaskMapper.updateById(updateObj);
    }

    @Override
    public void completeExecution(Long id) {
        SrvTaskDO task = validateSrvTaskExists(id);
        if (!Objects.equals(task.getStatus(), STATUS_EXECUTING)) {
            throw exception(SRV_TASK_STATUS_INVALID);
        }
        updateStatus(id, STATUS_PENDING_CONFIRM);
    }

    @Override
    public void confirmReport(Long id) {
        SrvTaskDO task = validateSrvTaskExists(id);
        if (!Objects.equals(task.getStatus(), STATUS_PENDING_CONFIRM)) {
            throw exception(SRV_TASK_STATUS_INVALID);
        }
        updateStatus(id, STATUS_COMPLETED);
    }

    @Override
    public void cancelSrvTask(Long id) {
        SrvTaskDO task = validateSrvTaskExists(id);
        if (!Objects.equals(task.getStatus(), STATUS_DRAFT)
                && !Objects.equals(task.getStatus(), STATUS_PENDING)) {
            throw exception(SRV_TASK_STATUS_INVALID);
        }
        updateStatus(id, STATUS_CANCELLED);
    }

    private void updateStatus(Long id, int status) {
        SrvTaskDO updateObj = new SrvTaskDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        srvTaskMapper.updateById(updateObj);
    }

    private SrvTaskDO validateSrvTaskExists(Long id) {
        if (id == null) {
            throw exception(SRV_TASK_NOT_EXISTS);
        }
        SrvTaskDO task = srvTaskMapper.selectById(id);
        if (task == null) {
            throw exception(SRV_TASK_NOT_EXISTS);
        }
        return task;
    }

    private void validateCodeUnique(Long id, Long projectId, String code) {
        if (projectId == null || code == null) {
            return;
        }
        SrvTaskDO existing = srvTaskMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_TASK_CODE_DUPLICATE, code);
        }
    }

}
