package cn.iocoder.yudao.module.pms.engineering.service.stageplan;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemUpdateReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueRowVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanOverdueSummaryVO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 阶段施工计划批次 Service（PLN-01/04，Demo 3.1）。
 * <p>
 * 批次生命周期：草稿 →（提交审批）审批中 →（BPM 通过）已生效 /（BPM 驳回）已驳回；
 * 已驳回可调整后重新提交（驳回重提）。
 * 推算：优先以已生效工期基线窗口按阶段建议工期占比倒推；无工期基线时按阶段建议起止直接带入。
 */
public interface StagePlanBatchService {

    /**
     * 创建阶段施工计划草稿：读取项目阶段事实生成明细（计划时间带入现有计划值，无则带入建议值）。
     * 同一项目同时仅允许一个草稿/审批中批次。
     */
    StagePlanBatchRespVO createDraft(Long projectId);

    /**
     * 自动推算：按阶段建议工期占比在工期基线窗口内倒推计划起止；无基线时带入建议起止。
     */
    StagePlanBatchRespVO autoEstimate(Long batchId);

    /**
     * 人工调整明细计划时间（仅草稿/已驳回可改），校验阶段间不重叠。
     */
    StagePlanBatchRespVO updateItems(@Valid StagePlanItemUpdateReqVO updateReqVO);

    /**
     * 提交审批：发起 BPM 流程，批次进入审批中；审批通过后经受控写入回写阶段计划日期并生效。
     */
    StagePlanBatchRespVO submit(Long batchId, Long approverUserId);

    /**
     * BPM 终态回执处理：APPROVED 生效回写；REJECTED 记录驳回原因。
     */
    void handleBpmResult(String processInstanceId, Integer bpmStatus, String reason);

    StagePlanBatchRespVO getBatch(Long batchId);

    PageResult<StagePlanBatchRespVO> getBatchPage(StagePlanBatchPageReqVO pageReqVO);

    /**
     * 超期阶段清单（PLN-03）：仅消费已生效计划批次，未完成阶段计划完成时间早于今天的计算结果；
     * projectId 为空时查询全部项目。超期天数按日历日计，标红为计算结果不允许人工修改。
     */
    List<StagePlanOverdueRowVO> getOverdueStages(Long projectId);

    /**
     * 超期统计汇总：与超期阶段清单同口径，按项目去重。
     */
    StagePlanOverdueSummaryVO getOverdueSummary(Long projectId);
}
