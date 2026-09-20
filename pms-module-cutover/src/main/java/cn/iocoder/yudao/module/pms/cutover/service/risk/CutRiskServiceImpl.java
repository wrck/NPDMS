package cn.iocoder.yudao.module.pms.cutover.service.risk;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.risk.vo.CutRiskSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.risk.CutRiskRetiredDO;
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
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Service
@Validated
@Slf4j
@Deprecated
public class CutRiskServiceImpl implements CutRiskService {

    @Resource
    private CutRiskMapper cutRiskMapper;

    @Override
    public Long createCutRiskRetired(CutRiskSaveReqVO createReqVO) {
        // 1. 校验编码在任务内唯一
        validateCodeUniqueInTask(null, createReqVO.getTaskId(), createReqVO.getCode());
        // 2. 转换并写入，初始状态为待处理
        CutRiskRetiredDO entity = BeanUtils.toBean(createReqVO, CutRiskRetiredDO.class);
        entity.setStatus(CutStatusEnum.CUT_RISK_OPEN);
        if (StringUtils.isBlank(entity.getRiskType())) {
            entity.setRiskType("RISK");
        }
        cutRiskMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateCutRiskRetired(CutRiskSaveReqVO updateReqVO) {
        // 1. 校验存在
        CutRiskRetiredDO existing = validateCutRiskExistsRetired(updateReqVO.getId());
        // 2. 编码不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(CUT_RISK_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 3. 已闭环不允许修改
        if (Objects.equals(CutStatusEnum.CUT_RISK_CLOSED, existing.getStatus())) {
            throw exception(CUT_RISK_STATUS_INVALID);
        }
        // 4. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        CutRiskRetiredDO update = BeanUtils.toBean(updateReqVO, CutRiskRetiredDO.class);
        cutRiskMapper.updateById(update);
    }

    @Override
    public void deleteCutRiskRetired(Long id) {
        validateCutRiskExistsRetired(id);
        cutRiskMapper.deleteById(id);
    }

    @Override
    public CutRiskRetiredDO getCutRiskRetired(Long id) {
        return cutRiskMapper.selectById(id);
    }

    @Override
    public CutRiskRetiredDO validateCutRiskExistsRetired(Long id) {
        CutRiskRetiredDO entity = cutRiskMapper.selectById(id);
        if (entity == null) {
            throw exception(CUT_RISK_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public PageResult<CutRiskRetiredDO> getCutRiskPageRetired(CutRiskPageReqVO pageReqVO) {
        return cutRiskMapper.selectPageRetired(pageReqVO);
    }

    @Override
    public List<CutRiskRetiredDO> getCutRiskListByTaskRetired(Long taskId) {
        return cutRiskMapper.selectListByTaskRetired(taskId);
    }

    @Override
    public void startProcessRetired(Long id) {
        CutRiskRetiredDO entity = validateCutRiskExistsRetired(id);
        if (!Objects.equals(CutStatusEnum.CUT_RISK_OPEN, entity.getStatus())) {
            throw exception(CUT_RISK_STATUS_INVALID);
        }
        CutRiskRetiredDO update = new CutRiskRetiredDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_RISK_PROCESSING);
        update.setVersion(entity.getVersion());
        cutRiskMapper.updateById(update);
    }

    @Override
    public void closeRetired(Long id) {
        CutRiskRetiredDO entity = validateCutRiskExistsRetired(id);
        if (Objects.equals(CutStatusEnum.CUT_RISK_CLOSED, entity.getStatus())) {
            throw exception(CUT_RISK_STATUS_INVALID);
        }
        CutRiskRetiredDO update = new CutRiskRetiredDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_RISK_CLOSED);
        update.setVersion(entity.getVersion());
        cutRiskMapper.updateById(update);
    }

    @Override
    public void suspendRetired(Long id) {
        CutRiskRetiredDO entity = validateCutRiskExistsRetired(id);
        if (Objects.equals(CutStatusEnum.CUT_RISK_CLOSED, entity.getStatus())
                || Objects.equals(CutStatusEnum.CUT_RISK_SUSPENDED, entity.getStatus())) {
            throw exception(CUT_RISK_STATUS_INVALID);
        }
        CutRiskRetiredDO update = new CutRiskRetiredDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_RISK_SUSPENDED);
        update.setVersion(entity.getVersion());
        cutRiskMapper.updateById(update);
    }

    private void validateCodeUniqueInTask(Long id, Long taskId, String code) {
        if (StringUtils.isBlank(code) || taskId == null) {
            return;
        }
        CutRiskRetiredDO existing = cutRiskMapper.selectByTaskCodeRetired(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(CUT_RISK_CODE_DUPLICATE, code);
        }
    }
}
