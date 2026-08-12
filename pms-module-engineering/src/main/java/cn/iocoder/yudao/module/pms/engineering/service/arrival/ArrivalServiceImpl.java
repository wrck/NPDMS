package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrival.vo.ArrivalSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 到货签收 Service 实现（FR-ENG-021）。
 * <p>
 * 状态流转：0 待签收 → 1 已签收 / 2 异常。
 */
@Service
@Validated
public class ArrivalServiceImpl implements ArrivalService {

    @Resource
    private ArrivalMapper arrivalMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createArrival(ArrivalSaveReqVO createReqVO) {
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode(), null);
        ArrivalDO arrival = BeanUtils.toBean(createReqVO, ArrivalDO.class);
        if (arrival.getStatus() == null) {
            arrival.setStatus(0); // 待签收
        }
        if (arrival.getVersion() == null) {
            arrival.setVersion(0);
        }
        if (arrival.getQuantity() == null) {
            arrival.setQuantity(1);
        }
        arrivalMapper.insert(arrival);
        return arrival.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArrival(ArrivalSaveReqVO updateReqVO) {
        ArrivalDO existing = validateArrivalExists(updateReqVO.getId());
        validateCodeUnique(existing.getProjectId(), updateReqVO.getCode(), updateReqVO.getId());
        validateVersion(existing, updateReqVO.getVersion());
        ArrivalDO update = BeanUtils.toBean(updateReqVO, ArrivalDO.class);
        arrivalMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArrival(Long id) {
        validateArrivalExists(id);
        arrivalMapper.deleteById(id);
    }

    @Override
    public ArrivalDO getArrival(Long id) {
        return arrivalMapper.selectById(id);
    }

    @Override
    public PageResult<ArrivalDO> getArrivalPage(ArrivalPageReqVO pageReqVO) {
        return arrivalMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void signArrival(Long id) {
        ArrivalDO arrival = validateArrivalExists(id);
        validateStatus(arrival, 0); // 待签收 → 已签收
        updateStatus(arrival, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAbnormal(Long id) {
        ArrivalDO arrival = validateArrivalExists(id);
        validateStatus(arrival, 0); // 待签收 → 异常
        updateStatus(arrival, 2);
    }

    // ==================== 内部工具方法 ====================

    private ArrivalDO validateArrivalExists(Long id) {
        ArrivalDO arrival = arrivalMapper.selectById(id);
        if (arrival == null) {
            throw exception(ARRIVAL_NOT_EXISTS);
        }
        return arrival;
    }

    private void validateCodeUnique(Long projectId, String code, Long excludeId) {
        ArrivalDO existing = arrivalMapper.selectByProjectIdAndCode(projectId, code);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(ARRIVAL_CODE_DUPLICATE);
        }
    }

    private void validateVersion(ArrivalDO arrival, Integer version) {
        if (version != null && !Objects.equals(arrival.getVersion(), version)) {
            throw exception(ARRIVAL_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(ArrivalDO arrival, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(arrival.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(ARRIVAL_STATUS_INVALID);
    }

    private void updateStatus(ArrivalDO arrival, int newStatus) {
        arrival.setStatus(newStatus);
        arrival.setVersion(arrival.getVersion() + 1);
        arrivalMapper.updateById(arrival);
    }
}
