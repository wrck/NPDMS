package cn.iocoder.yudao.module.pms.engineering.service.briefing;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingGenerateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.briefing.vo.BriefingSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.briefing.BriefingDO;
import jakarta.validation.Valid;

/**
 * PMS 工程交底书 Service 接口（FR-ENG-006）。
 * <p>
 * 状态流转：0 草稿 → 1 已生成 → 2 已审核 → 3 已发布；任意非已发布状态可作废为 4 已作废。
 * 交底书编号全局唯一；草稿状态可编辑或删除。
 */
public interface BriefingService {

    /**
     * 创建交底书（校验编号唯一 + 项目存在）
     *
     * @param createReqVO 创建信息
     * @return 交底书编号
     */
    Long createBriefing(@Valid BriefingSaveReqVO createReqVO);

    /**
     * 更新交底书（仅 0 草稿 状态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updateBriefing(@Valid BriefingSaveReqVO updateReqVO);

    /**
     * 删除交底书（仅 0 草稿 状态可删）
     *
     * @param id 交底书编号
     */
    void deleteBriefing(Long id);

    /**
     * 查询交底书详情
     *
     * @param id 交底书编号
     * @return 交底书对象
     */
    BriefingDO getBriefing(Long id);

    /**
     * 校验交底书存在
     *
     * @param id 交底书编号
     * @return 交底书对象
     */
    BriefingDO validateBriefingExists(Long id);

    /**
     * 分页查询交底书
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<BriefingDO> getBriefingPage(BriefingPageReqVO pageReqVO);

    /**
     * 生成交底书：0 草稿 → 1 已生成
     * <p>
     * 按模板和前序基线数据快照生成内容与文件，记录生成时间。
     *
     * @param reqVO 生成信息
     */
    void generateBriefing(@Valid BriefingGenerateReqVO reqVO);

    /**
     * 审核交底书：1 已生成 → 2 已审核 / 0 草稿（驳回）
     *
     * @param reqVO 审核信息
     */
    void approveBriefing(@Valid BriefingApproveReqVO reqVO);

    /**
     * 发布交底书：2 已审核 → 3 已发布
     *
     * @param id 交底书编号
     */
    void publishBriefing(Long id);

    /**
     * 作废交底书：非 3 已发布 / 非 4 已作废 → 4 已作废
     *
     * @param id 交底书编号
     */
    void terminateBriefing(Long id);
}
