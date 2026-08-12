package cn.iocoder.yudao.module.pms.engineering.service.deliverable;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.deliverable.DeliverableMapper;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 阶段交付件归集 Service 实现（FR-ENG-027）。
 * <p>
 * 归集版本不可覆盖：同一来源业务再次归集时返回已存在记录，不创建新版本。
 * 已归集（status=1）的交付件不可修改/删除，仅可作废。
 */
@Service
@Validated
@Slf4j
public class DeliverableServiceImpl implements DeliverableService {

    @Resource
    private DeliverableMapper deliverableMapper;

    @Override
    public Long createDeliverable(DeliverableSaveReqVO createReqVO) {
        validateCodeUniqueInProject(null, createReqVO.getProjectId(), createReqVO.getCode());
        DeliverableDO entity = BeanUtils.toBean(createReqVO, DeliverableDO.class);
        entity.setStatus(EngStatusEnum.DELIVERABLE_PENDING);
        deliverableMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateDeliverable(DeliverableSaveReqVO updateReqVO) {
        DeliverableDO existing = validateDeliverableExists(updateReqVO.getId());
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(DELIVERABLE_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 已归集不可修改（归集版本不可覆盖）
        if (Objects.equals(EngStatusEnum.DELIVERABLE_ARCHIVED, existing.getStatus())) {
            throw exception(DELIVERABLE_STATUS_INVALID);
        }
        DeliverableDO update = BeanUtils.toBean(updateReqVO, DeliverableDO.class);
        deliverableMapper.updateById(update);
    }

    @Override
    public void deleteDeliverable(Long id) {
        DeliverableDO existing = validateDeliverableExists(id);
        // 已归集不可删除
        if (Objects.equals(EngStatusEnum.DELIVERABLE_ARCHIVED, existing.getStatus())) {
            throw exception(DELIVERABLE_STATUS_INVALID);
        }
        deliverableMapper.deleteById(id);
    }

    @Override
    public DeliverableDO getDeliverable(Long id) {
        return deliverableMapper.selectById(id);
    }

    @Override
    public DeliverableDO validateDeliverableExists(Long id) {
        DeliverableDO entity = deliverableMapper.selectById(id);
        if (entity == null) {
            throw exception(DELIVERABLE_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<DeliverableDO> getDeliverablePage(DeliverablePageReqVO pageReqVO) {
        return deliverableMapper.selectPage(pageReqVO);
    }

    @Override
    public Long archive(Long id, Long archivedBy) {
        DeliverableDO existing = validateDeliverableExists(id);
        // 归集版本不可覆盖：若已归集，直接返回已存在编号（幂等）
        if (Objects.equals(EngStatusEnum.DELIVERABLE_ARCHIVED, existing.getStatus())) {
            log.info("交付件 {} 已归集，幂等返回，不覆盖版本", id);
            return id;
        }
        // 仅待归集状态可归集
        if (!Objects.equals(EngStatusEnum.DELIVERABLE_PENDING, existing.getStatus())) {
            throw exception(DELIVERABLE_STATUS_INVALID);
        }
        DeliverableDO update = new DeliverableDO();
        update.setId(id);
        update.setStatus(EngStatusEnum.DELIVERABLE_ARCHIVED);
        update.setArchivedBy(archivedBy);
        update.setArchivedTime(LocalDateTime.now());
        update.setVersion(existing.getVersion());
        deliverableMapper.updateById(update);
        return id;
    }

    @Override
    public void voidDeliverable(Long id) {
        DeliverableDO existing = validateDeliverableExists(id);
        // 已作废不可重复作废
        if (Objects.equals(EngStatusEnum.DELIVERABLE_VOID, existing.getStatus())) {
            return;
        }
        DeliverableDO update = new DeliverableDO();
        update.setId(id);
        update.setStatus(EngStatusEnum.DELIVERABLE_VOID);
        update.setVersion(existing.getVersion());
        deliverableMapper.updateById(update);
    }

    private void validateCodeUniqueInProject(Long id, Long projectId, String code) {
        if (StringUtils.isBlank(code) || projectId == null) {
            return;
        }
        DeliverableDO existing = deliverableMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(DELIVERABLE_CODE_DUPLICATE, code);
        }
    }
}
