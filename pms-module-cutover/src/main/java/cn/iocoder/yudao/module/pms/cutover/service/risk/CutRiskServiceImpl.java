package cn.iocoder.yudao.module.pms.cutover.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.risk.CutRiskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.risk.CutRiskMapper;
import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.*;

/**
 * PMS 割接风险 Service 实现（FR-CUT-004 / FR-CUT-006）。
 */
@Service
@Validated
@Slf4j
public class CutRiskServiceImpl implements CutRiskService {

    @Resource
    private CutRiskMapper cutRiskMapper;

    @Override
    public Long createCutRisk(CutRiskSaveReqVO createReqVO) {
        // 1. 校验编码在任务内唯一
        validateCodeUniqueInTask(null, createReqVO.getTaskId(), createReqVO.getCode());
        // 2. 转换并写入，初始状态为待处理
        CutRiskDO entity = BeanUtils.toBean(createReqVO, CutRiskDO.class);
        entity.setStatus(CutStatusEnum.CUT_RISK_OPEN);
        if (StringUtils.isBlank(entity.getRiskType())) {
            entity.setRiskType("RISK");
        }
        cutRiskMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateCutRisk(CutRiskSaveReqVO updateReqVO) {
        // 1. 校验存在
        CutRiskDO existing = validateCutRiskExists(updateReqVO.getId());
        // 2. 编码不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(CUT_RISK_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 3. 已闭环不允许修改
        if (Objects.equals(CutStatusEnum.CUT_RISK_CLOSED, existing.getStatus())) {
            throw exception(CUT_RISK_STATUS_INVALID);
        }
        // 4. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        CutRiskDO update = BeanUtils.toBean(updateReqVO, CutRiskDO.class);
        cutRiskMapper.updateById(update);
    }

    @Override
    public void deleteCutRisk(Long id) {
        validateCutRiskExists(id);
        cutRiskMapper.deleteById(id);
    }

    @Override
    public CutRiskDO getCutRisk(Long id) {
        return cutRiskMapper.selectById(id);
    }

    @Override
    public CutRiskDO validateCutRiskExists(Long id) {
        CutRiskDO entity = cutRiskMapper.selectById(id);
        if (entity == null) {
            throw exception(CUT_RISK_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public PageResult<CutRiskDO> getCutRiskPage(CutRiskPageReqVO pageReqVO) {
        return cutRiskMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CutRiskDO> getCutRiskListByTask(Long taskId) {
        return cutRiskMapper.selectListByTask(taskId);
    }

    @Override
    public void startProcess(Long id) {
        CutRiskDO entity = validateCutRiskExists(id);
        if (!Objects.equals(CutStatusEnum.CUT_RISK_OPEN, entity.getStatus())) {
            throw exception(CUT_RISK_STATUS_INVALID);
        }
        CutRiskDO update = new CutRiskDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_RISK_PROCESSING);
        update.setVersion(entity.getVersion());
        cutRiskMapper.updateById(update);
    }

    @Override
    public void close(Long id) {
        CutRiskDO entity = validateCutRiskExists(id);
        if (Objects.equals(CutStatusEnum.CUT_RISK_CLOSED, entity.getStatus())) {
            throw exception(CUT_RISK_STATUS_INVALID);
        }
        CutRiskDO update = new CutRiskDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_RISK_CLOSED);
        update.setVersion(entity.getVersion());
        cutRiskMapper.updateById(update);
    }

    @Override
    public void suspend(Long id) {
        CutRiskDO entity = validateCutRiskExists(id);
        if (Objects.equals(CutStatusEnum.CUT_RISK_CLOSED, entity.getStatus())
                || Objects.equals(CutStatusEnum.CUT_RISK_SUSPENDED, entity.getStatus())) {
            throw exception(CUT_RISK_STATUS_INVALID);
        }
        CutRiskDO update = new CutRiskDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_RISK_SUSPENDED);
        update.setVersion(entity.getVersion());
        cutRiskMapper.updateById(update);
    }

    private void validateCodeUniqueInTask(Long id, Long taskId, String code) {
        if (StringUtils.isBlank(code) || taskId == null) {
            return;
        }
        CutRiskDO existing = cutRiskMapper.selectByTaskCode(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(CUT_RISK_CODE_DUPLICATE, code);
        }
    }
}
