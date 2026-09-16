package cn.iocoder.yudao.module.pms.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalHistory;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import cn.iocoder.yudao.module.pms.workflow.vo.ApprovalStatisticsVO;

import java.util.List;

/**
 * 统一审批中心服务（Story 6）。
 *
 * <p>承载审批记录的创建、流转（通过/退回/撤回/重新提交）与查询。每次操作都追加
 * {@link ApprovalHistory} 历史记录，支持多轮次追溯。审批退回后重新提交复用原审批记录，
 * {@code round} 字段递增。</p>
 *
 * <p>状态机：{@code [DRAFT] → [PENDING] → [APPROVED]}；PENDING 可流转至
 * {@code REJECTED / WITHDRAWN / TIMEOUT}；REJECTED 重新提交后 round+1 回到 PENDING。</p>
 *
 * <p>关联设计文档：§3.5 审批中心统一规则（行 429-500）、§5.7 统一审批中心 API（行 1080-1147）。</p>
 */
public interface ApprovalCenterService extends IService<ApprovalRecord> {

    /**
     * 创建审批 — 状态 PENDING，round=1。
     *
     * <p>同时记录 SUBMIT 历史动作；若 Flowable 可用则启动流程实例并存入
     * {@code processInstanceId}（由 {@link ApprovalDispatcher} 或子类触发）。</p>
     *
     * @param record 审批记录（approvalType/businessId/title/submitterId 必填）
     * @return 已创建的审批记录（含 ID 与初始状态）
     */
    ApprovalRecord createApproval(ApprovalRecord record);

    /**
     * 通过当前节点 — 当前节点状态置 APPROVED，激活下一节点；若为最后节点则将审批记录
     * 状态置为 APPROVED。
     *
     * @param nodeId     审批节点ID
     * @param comment    审批意见
     * @param operatorId 操作人ID
     * @return 更新后的审批记录
     */
    ApprovalRecord approve(Long nodeId, String comment, Long operatorId);

    /**
     * 退回 — 当前节点状态置 REJECTED，审批记录状态置 REJECTED（round 不变，重新提交时 +1）。
     *
     * @param nodeId     审批节点ID
     * @param comment    退回意见
     * @param operatorId 操作人ID
     * @return 更新后的审批记录
     */
    ApprovalRecord reject(Long nodeId, String comment, Long operatorId);

    /**
     * 撤回 — 仅提交人可撤回；审批记录状态置 WITHDRAWN。
     *
     * @param recordId   审批记录ID
     * @param operatorId 操作人ID（校验是否为提交人）
     * @return 更新后的审批记录
     */
    ApprovalRecord withdraw(Long recordId, Long operatorId);

    /**
     * 重新提交 — 复用原审批记录，round+1，状态回 PENDING；重置节点状态。
     *
     * @param recordId 审批记录ID
     * @param comment  重新提交说明
     * @return 更新后的审批记录
     */
    ApprovalRecord resubmit(Long recordId, String comment);

    /**
     * 我的待办 — 当前用户作为审批人的 PENDING 节点对应的审批记录。
     *
     * @param userId 用户ID
     * @return 审批记录列表
     */
    List<ApprovalRecord> listPending(Long userId);

    /**
     * 我提交的 — 当前用户提交的审批记录。
     *
     * @param userId 用户ID
     * @return 审批记录列表
     */
    List<ApprovalRecord> listSubmitted(Long userId);

    /**
     * 项目维度审批列表。
     *
     * @param projectId 项目ID
     * @return 审批记录列表
     */
    List<ApprovalRecord> listByProject(Long projectId);

    /**
     * 审批历史（含所有轮次，按 round、operatedAt 升序）。
     *
     * @param recordId 审批记录ID
     * @return 历史列表
     */
    List<ApprovalHistory> listHistory(Long recordId);

    /**
     * 审批统计 — 按状态聚合。
     *
     * @param userId 用户ID（为空则统计全量）
     * @return 统计 VO
     */
    ApprovalStatisticsVO statistics(Long userId);
}
