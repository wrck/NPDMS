package cn.iocoder.yudao.module.pms.engineering.service.announcement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo.AnnouncementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo.AnnouncementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcement.AnnouncementDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.announcement.AnnouncementMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 技术公告 Service 实现（FR-ENG-009）。
 * <p>
 * 状态流转：0 草稿 → 1 已发布 → 2 已停用。
 * 公告编号全局唯一；草稿状态可编辑或删除；已发布后不可编辑。
 */
@Service
@Validated
@Slf4j
public class AnnouncementServiceImpl implements AnnouncementService {

    /**
     * 状态：0 草稿
     */
    public static final int STATUS_DRAFT = 0;
    /**
     * 状态：1 已发布
     */
    public static final int STATUS_PUBLISHED = 1;
    /**
     * 状态：2 已停用
     */
    public static final int STATUS_DISABLED = 2;

    @Resource
    private AnnouncementMapper announcementMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAnnouncement(AnnouncementSaveReqVO createReqVO) {
        // 1. 校验编号全局唯一
        validateCodeUnique(createReqVO.getCode(), null);
        // 2. 转换并写入，初始状态为草稿
        AnnouncementDO entity = BeanUtils.toBean(createReqVO, AnnouncementDO.class);
        entity.setStatus(STATUS_DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        // 默认公告类型与严重等级
        if (StringUtils.isBlank(entity.getAnnouncementType())) {
            entity.setAnnouncementType("TECH_NOTICE");
        }
        if (StringUtils.isBlank(entity.getSeverity())) {
            entity.setSeverity("MEDIUM");
        }
        announcementMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(AnnouncementSaveReqVO updateReqVO) {
        // 1. 校验存在
        AnnouncementDO existing = validateAnnouncementExists(updateReqVO.getId());
        // 2. 状态校验：仅 0 草稿 可编辑
        validateStatus(existing, STATUS_DRAFT);
        // 3. 乐观锁版本校验
        validateVersion(existing, updateReqVO.getVersion());
        // 4. 编号不可变
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(ANNOUNCEMENT_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 5. 更新
        AnnouncementDO update = BeanUtils.toBean(updateReqVO, AnnouncementDO.class);
        announcementMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnnouncement(Long id) {
        // 1. 校验存在
        AnnouncementDO existing = validateAnnouncementExists(id);
        // 2. 状态校验：仅 0 草稿 可删除
        validateStatus(existing, STATUS_DRAFT);
        // 3. 删除
        announcementMapper.deleteById(id);
    }

    @Override
    public AnnouncementDO getAnnouncement(Long id) {
        return announcementMapper.selectById(id);
    }

    @Override
    public AnnouncementDO validateAnnouncementExists(Long id) {
        AnnouncementDO entity = announcementMapper.selectById(id);
        if (entity == null) {
            throw exception(ANNOUNCEMENT_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<AnnouncementDO> getAnnouncementPage(AnnouncementPageReqVO pageReqVO) {
        return announcementMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishAnnouncement(Long id) {
        // 1. 校验存在
        AnnouncementDO entity = validateAnnouncementExists(id);
        // 2. 状态校验：0 草稿 → 1 已发布
        validateStatus(entity, STATUS_DRAFT);
        // 3. 自动填充发布日期（若未填写）
        if (entity.getPublishDate() == null) {
            entity.setPublishDate(LocalDate.now());
        }
        // 4. 更新状态
        entity.setStatus(STATUS_PUBLISHED);
        entity.setVersion(entity.getVersion() + 1);
        announcementMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableAnnouncement(Long id) {
        // 1. 校验存在
        AnnouncementDO entity = validateAnnouncementExists(id);
        // 2. 状态校验：1 已发布 → 2 已停用
        validateStatus(entity, STATUS_PUBLISHED);
        // 3. 更新状态
        entity.setStatus(STATUS_DISABLED);
        entity.setVersion(entity.getVersion() + 1);
        announcementMapper.updateById(entity);
    }

    // ==================== 内部工具方法 ====================

    private void validateCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        AnnouncementDO existing = announcementMapper.selectByCode(code);
        if (existing == null) {
            return;
        }
        if (excludeId == null || !Objects.equals(existing.getId(), excludeId)) {
            throw exception(ANNOUNCEMENT_CODE_DUPLICATE, code);
        }
    }

    private void validateVersion(AnnouncementDO entity, Integer version) {
        if (version != null && !Objects.equals(entity.getVersion(), version)) {
            throw exception(ANNOUNCEMENT_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(AnnouncementDO entity, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(entity.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(ANNOUNCEMENT_STATUS_INVALID);
    }
}
