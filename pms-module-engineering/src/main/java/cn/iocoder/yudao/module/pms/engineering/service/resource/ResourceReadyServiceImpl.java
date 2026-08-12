package cn.iocoder.yudao.module.pms.engineering.service.resource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo.ResourceReadyPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.resource.vo.ResourceReadySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.resource.ResourceReadyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.resource.ResourceReadyMapper;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 资源与备件就绪 Service 实现（FR-ENG-018）。
 */
@Service
@Validated
@Slf4j
public class ResourceReadyServiceImpl implements ResourceReadyService {

    @Resource
    private ResourceReadyMapper resourceReadyMapper;

    @Override
    public Long createResourceReady(ResourceReadySaveReqVO createReqVO) {
        // 1. 校验编码在项目内唯一
        validateCodeUniqueInProject(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 2. 转换并写入，初始状态为未就绪
        ResourceReadyDO entity = BeanUtils.toBean(createReqVO, ResourceReadyDO.class);
        entity.setReadyStatus(EngStatusEnum.RESOURCE_NOT_READY);
        resourceReadyMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateResourceReady(ResourceReadySaveReqVO updateReqVO) {
        // 1. 校验存在
        ResourceReadyDO existing = validateResourceReadyExists(updateReqVO.getId());
        // 2. 编码不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(RESOURCE_READY_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 3. 更新（乐观锁由 MyBatis-Plus @Version 自动处理）
        ResourceReadyDO update = BeanUtils.toBean(updateReqVO, ResourceReadyDO.class);
        resourceReadyMapper.updateById(update);
    }

    @Override
    public void deleteResourceReady(Long id) {
        validateResourceReadyExists(id);
        resourceReadyMapper.deleteById(id);
    }

    @Override
    public ResourceReadyDO getResourceReady(Long id) {
        return resourceReadyMapper.selectById(id);
    }

    @Override
    public ResourceReadyDO validateResourceReadyExists(Long id) {
        ResourceReadyDO entity = resourceReadyMapper.selectById(id);
        if (entity == null) {
            throw exception(RESOURCE_READY_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<ResourceReadyDO> getResourceReadyPage(ResourceReadyPageReqVO pageReqVO) {
        return resourceReadyMapper.selectPage(pageReqVO);
    }

    @Override
    public void markReady(Long id) {
        // 1. 校验存在
        ResourceReadyDO entity = validateResourceReadyExists(id);
        // 2. 状态校验：0未就绪 → 1已就绪
        if (!Objects.equals(EngStatusEnum.RESOURCE_NOT_READY, entity.getReadyStatus())) {
            throw exception(RESOURCE_READY_STATUS_INVALID, entity.getCode());
        }
        // 3. 更新状态、就绪时间与确认人
        ResourceReadyDO update = new ResourceReadyDO();
        update.setId(id);
        update.setReadyStatus(EngStatusEnum.RESOURCE_READY);
        update.setReadyTime(LocalDateTime.now());
        update.setReadyUserId(getLoginUserId());
        update.setVersion(entity.getVersion());
        resourceReadyMapper.updateById(update);
    }

    @Override
    public void markAbnormal(Long id) {
        // 1. 校验存在
        ResourceReadyDO entity = validateResourceReadyExists(id);
        // 2. 状态校验：0未就绪 / 1已就绪 → 2异常
        if (!Objects.equals(EngStatusEnum.RESOURCE_NOT_READY, entity.getReadyStatus())
                && !Objects.equals(EngStatusEnum.RESOURCE_READY, entity.getReadyStatus())) {
            throw exception(RESOURCE_READY_STATUS_INVALID, entity.getCode());
        }
        // 3. 更新状态
        ResourceReadyDO update = new ResourceReadyDO();
        update.setId(id);
        update.setReadyStatus(EngStatusEnum.RESOURCE_ABNORMAL);
        update.setVersion(entity.getVersion());
        resourceReadyMapper.updateById(update);
    }

    @Override
    public void resetToNotReady(Long id) {
        // 1. 校验存在
        ResourceReadyDO entity = validateResourceReadyExists(id);
        // 2. 状态校验：1已就绪 / 2异常 → 0未就绪
        if (!Objects.equals(EngStatusEnum.RESOURCE_READY, entity.getReadyStatus())
                && !Objects.equals(EngStatusEnum.RESOURCE_ABNORMAL, entity.getReadyStatus())) {
            throw exception(RESOURCE_READY_STATUS_INVALID, entity.getCode());
        }
        // 3. 更新状态，清空就绪信息
        ResourceReadyDO update = new ResourceReadyDO();
        update.setId(id);
        update.setReadyStatus(EngStatusEnum.RESOURCE_NOT_READY);
        update.setReadyTime(null);
        update.setReadyUserId(null);
        update.setVersion(entity.getVersion());
        resourceReadyMapper.updateById(update);
    }

    @Override
    public void validateProjectResourceReady(Long projectId) {
        // 项目下存在未就绪资源（ready_status != 1）时阻断后续实施动作
        Long count = resourceReadyMapper.selectCountByProjectNotReady(projectId);
        if (count != null && count > 0) {
            throw exception(RESOURCE_READY_STATUS_INVALID, projectId);
        }
    }

    private void validateCodeUniqueInProject(Long id, Long projectId, String code) {
        if (StringUtils.isBlank(code) || projectId == null) {
            return;
        }
        ResourceReadyDO existing = resourceReadyMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(RESOURCE_READY_CODE_DUPLICATE, code);
        }
    }
}
