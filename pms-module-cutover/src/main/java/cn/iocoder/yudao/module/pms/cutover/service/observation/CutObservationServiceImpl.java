package cn.iocoder.yudao.module.pms.cutover.service.observation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo.CutObservationPageReqVO;
import cn.iocoder.yudao.module.pms.cutover.controller.admin.observation.vo.CutObservationSaveReqVO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.observation.CutObservationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.observation.CutObservationMapper;
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
import static cn.iocoder.yudao.module.pms.cutover.enums.ErrorCodeConstants.*;

/**
 * PMS 稳定观察 Service 实现（FR-CUT-013 / FR-CUT-014）。
 */
@Service
@Validated
@Slf4j
public class CutObservationServiceImpl implements CutObservationService {

    @Resource
    private CutObservationMapper cutObservationMapper;

    @Override
    public Long createCutObservation(CutObservationSaveReqVO createReqVO) {
        // 1. 校验编码在任务内唯一
        validateCodeUniqueInTask(null, createReqVO.getTaskId(), createReqVO.getCode());
        // 2. 转换并写入，初始状态为观察中
        CutObservationDO entity = BeanUtils.toBean(createReqVO, CutObservationDO.class);
        entity.setStatus(CutStatusEnum.CUT_OBSERVATION_OBSERVING);
        if (entity.getLeftoverStatus() == null) {
            entity.setLeftoverStatus(0);
        }
        if (entity.getObservationStart() == null) {
            entity.setObservationStart(LocalDateTime.now());
        }
        cutObservationMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateCutObservation(CutObservationSaveReqVO updateReqVO) {
        // 1. 校验存在
        CutObservationDO existing = validateCutObservationExists(updateReqVO.getId());
        // 2. 编码不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(CUT_OBSERVATION_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 3. 已归档不允许修改
        if (Objects.equals(CutStatusEnum.CUT_OBSERVATION_ARCHIVED, existing.getStatus())) {
            throw exception(CUT_OBSERVATION_STATUS_INVALID);
        }
        // 4. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        CutObservationDO update = BeanUtils.toBean(updateReqVO, CutObservationDO.class);
        cutObservationMapper.updateById(update);
    }

    @Override
    public void deleteCutObservation(Long id) {
        validateCutObservationExists(id);
        cutObservationMapper.deleteById(id);
    }

    @Override
    public CutObservationDO getCutObservation(Long id) {
        return cutObservationMapper.selectById(id);
    }

    @Override
    public CutObservationDO validateCutObservationExists(Long id) {
        CutObservationDO entity = cutObservationMapper.selectById(id);
        if (entity == null) {
            throw exception(CUT_OBSERVATION_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public PageResult<CutObservationDO> getCutObservationPage(CutObservationPageReqVO pageReqVO) {
        return cutObservationMapper.selectPage(pageReqVO);
    }

    @Override
    public List<CutObservationDO> getCutObservationListByTask(Long taskId) {
        return cutObservationMapper.selectListByTask(taskId);
    }

    @Override
    public void pass(Long id) {
        CutObservationDO entity = validateCutObservationExists(id);
        if (!Objects.equals(CutStatusEnum.CUT_OBSERVATION_OBSERVING, entity.getStatus())) {
            throw exception(CUT_OBSERVATION_STATUS_INVALID);
        }
        CutObservationDO update = new CutObservationDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_OBSERVATION_PASSED);
        if (entity.getObservationEnd() == null) {
            update.setObservationEnd(LocalDateTime.now());
        }
        update.setVersion(entity.getVersion());
        cutObservationMapper.updateById(update);
    }

    @Override
    public void markAbnormal(Long id) {
        CutObservationDO entity = validateCutObservationExists(id);
        if (!Objects.equals(CutStatusEnum.CUT_OBSERVATION_OBSERVING, entity.getStatus())) {
            throw exception(CUT_OBSERVATION_STATUS_INVALID);
        }
        CutObservationDO update = new CutObservationDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_OBSERVATION_ABNORMAL);
        update.setVersion(entity.getVersion());
        cutObservationMapper.updateById(update);
    }

    @Override
    public void archive(Long id) {
        CutObservationDO entity = validateCutObservationExists(id);
        if (!Objects.equals(CutStatusEnum.CUT_OBSERVATION_PASSED, entity.getStatus())) {
            throw exception(CUT_OBSERVATION_STATUS_INVALID);
        }
        // 归档前校验遗留项已闭环：leftover_status 必须为 0无遗留 或 2已闭环
        if (entity.getLeftoverStatus() != null && entity.getLeftoverStatus() == 1) {
            throw exception(CUT_OBSERVATION_LEFTOVER_NOT_CLOSED, id);
        }
        CutObservationDO update = new CutObservationDO();
        update.setId(id);
        update.setStatus(CutStatusEnum.CUT_OBSERVATION_ARCHIVED);
        update.setVersion(entity.getVersion());
        cutObservationMapper.updateById(update);
    }

    private void validateCodeUniqueInTask(Long id, Long taskId, String code) {
        if (StringUtils.isBlank(code) || taskId == null) {
            return;
        }
        CutObservationDO existing = cutObservationMapper.selectByTaskCode(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(CUT_OBSERVATION_CODE_DUPLICATE, code);
        }
    }
}
