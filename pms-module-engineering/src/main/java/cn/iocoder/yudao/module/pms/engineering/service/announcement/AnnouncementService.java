package cn.iocoder.yudao.module.pms.engineering.service.announcement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo.AnnouncementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcement.vo.AnnouncementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcement.AnnouncementDO;

/**
 * PMS 技术公告 Service 接口（FR-ENG-009）。
 * <p>
 * 状态流转：0 草稿 → 1 已发布 → 2 已停用。
 */
public interface AnnouncementService {

    /**
     * 创建技术公告
     */
    Long createAnnouncement(AnnouncementSaveReqVO createReqVO);

    /**
     * 更新技术公告（仅草稿状态可编辑）
     */
    void updateAnnouncement(AnnouncementSaveReqVO updateReqVO);

    /**
     * 删除技术公告（仅草稿状态可删除）
     */
    void deleteAnnouncement(Long id);

    /**
     * 查询技术公告详情
     */
    AnnouncementDO getAnnouncement(Long id);

    /**
     * 校验技术公告存在，不存在则抛异常
     */
    AnnouncementDO validateAnnouncementExists(Long id);

    /**
     * 分页查询
     */
    PageResult<AnnouncementDO> getAnnouncementPage(AnnouncementPageReqVO pageReqVO);

    /**
     * 发布技术公告（0 草稿 → 1 已发布）
     */
    void publishAnnouncement(Long id);

    /**
     * 停用技术公告（1 已发布 → 2 已停用）
     */
    void disableAnnouncement(Long id);
}
