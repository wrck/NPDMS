package cn.iocoder.yudao.module.pms.engineering.service.announcementcheck;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckHandleReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.announcementcheck.vo.AnnouncementCheckSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.announcementcheck.AnnouncementCheckDO;

/**
 * PMS 公告预检查 Service 接口（FR-ENG-009）。
 * <p>
 * 状态流转：0 待检查 → 1 已检查 → 2 已处置 / 3 已忽略。
 */
public interface AnnouncementCheckService {

    /**
     * 创建预检查记录（自动执行匹配规则，初始状态为待检查）
     */
    Long createAnnouncementCheck(AnnouncementCheckSaveReqVO createReqVO);

    /**
     * 更新预检查记录（仅待检查状态可编辑）
     */
    void updateAnnouncementCheck(AnnouncementCheckSaveReqVO updateReqVO);

    /**
     * 删除预检查记录（仅待检查状态可删除）
     */
    void deleteAnnouncementCheck(Long id);

    /**
     * 查询预检查记录详情
     */
    AnnouncementCheckDO getAnnouncementCheck(Long id);

    /**
     * 校验预检查记录存在，不存在则抛异常
     */
    AnnouncementCheckDO validateAnnouncementCheckExists(Long id);

    /**
     * 分页查询
     */
    PageResult<AnnouncementCheckDO> getAnnouncementCheckPage(AnnouncementCheckPageReqVO pageReqVO);

    /**
     * 执行检查（0 待检查 → 1 已检查）：匹配公告与设备版本，输出匹配结果和EOS/EOM状态
     */
    void performCheck(Long id);

    /**
     * 处置检查记录（1 已检查 → 2 已处置 / 3 已忽略）
     */
    void handleCheck(AnnouncementCheckHandleReqVO reqVO);
}
