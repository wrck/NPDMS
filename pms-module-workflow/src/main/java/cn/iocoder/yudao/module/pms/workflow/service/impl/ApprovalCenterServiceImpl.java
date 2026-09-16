package cn.iocoder.yudao.module.pms.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.iocoder.yudao.module.pms.workflow.exception.BusinessException;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalHistory;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalNode;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalHistoryMapper;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalRecordMapper;
import cn.iocoder.yudao.module.pms.workflow.service.ApprovalCenterService;
import cn.iocoder.yudao.module.pms.workflow.vo.ApprovalStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一审批中心服务实现（Story 6）。
 *
 * <p>核心状态机：每次操作（创建/通过/退回/撤回/重新提交）都追加 {@link ApprovalHistory}
 * 历史记录，保证多轮次可追溯。退回后重新提交复用原审批记录，{@code round} 递增；
 * 节点状态在重新提交时重置为 PENDING。</p>
 *
 * <p>关联设计文档：§3.5 审批中心统一规则（行 429-500）、Story 6 验收 2 审批历史保留。</p>
 *
 * <p>Flowable 集成：{@code createApproval} 中尝试启动 BPMN 流程实例并将
 * {@code processInstanceId} 写入审批记录。Flowable 不可用（未部署对应流程定义或引擎未启动）
 * 时仅记录日志不阻断审批创建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalCenterServiceImpl extends ServiceImpl<ApprovalRecordMapper, ApprovalRecord>
        implements ApprovalCenterService {

    private final ApprovalNodeMapper approvalNodeMapper;
    private final ApprovalHistoryMapper approvalHistoryMapper;

    /** Flowable 运行时服务（可选注入，引擎未启用时为 null）。 */
    @Autowired(required = false)
    private RuntimeService runtimeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecord createApproval(ApprovalRecord record) {
        validateCreate(record);

        record.setStatus("PENDING");
        record.setRound(1);
        record.setSubmittedAt(LocalDateTime.now());
        record.setEscalated(false);
        this.save(record);

        // 记录 SUBMIT 历史
        recordHistory(record.getId(), record.getRound(), "提交", record.getSubmitterId(),
                record.getSubmitterName(), "SUBMIT", null);

        // 启动 Flowable 流程实例：以 approvalType（小写）作为流程 key
        startFlowableProcess(record);

        log.info("审批记录已创建：recordId={}, type={}, businessId={}, round={}, processInstanceId={}",
                record.getId(), record.getApprovalType(), record.getBusinessId(),
                record.getRound(), record.getProcessInstanceId());

        return record;
    }

    /**
     * 启动 Flowable 流程实例并将 processInstanceId 写回审批记录。
     *
     * <p>流程 key 取 {@code approvalType} 小写形式（如 {@code BASELINE_CHANGE} →
     * {@code baseline_change}）。Flowable 不可用（runtimeService 为 null）或流程定义未部署
     * 时仅记录日志不阻断，processInstanceId 留空。</p>
     */
    private void startFlowableProcess(ApprovalRecord record) {
        if (runtimeService == null) {
            log.info("Flowable RuntimeService 未启用，跳过流程实例启动：recordId={}", record.getId());
            return;
        }
        String processKey = record.getApprovalType() == null
                ? "approval"
                : record.getApprovalType().toLowerCase();
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("approvalRecordId", record.getId());
            variables.put("approvalType", record.getApprovalType());
            variables.put("businessId", record.getBusinessId());
            variables.put("submitterId", record.getSubmitterId());
            variables.put("round", record.getRound());
            if (record.getProjectId() != null) {
                variables.put("projectId", record.getProjectId());
            }
            ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                    processKey, String.valueOf(record.getId()), variables);
            record.setProcessInstanceId(instance.getId());
            this.updateById(record);
            log.info("Flowable 流程实例已启动：recordId={}, processKey={}, processInstanceId={}",
                    record.getId(), processKey, instance.getId());
        } catch (Exception e) {
            // 流程定义未部署或启动失败：不阻断审批创建
            log.warn("Flowable 流程实例启动失败（不阻断审批创建）：recordId={}, processKey={}, cause={}",
                    record.getId(), processKey, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecord approve(Long nodeId, String comment, Long operatorId) {
        ApprovalNode node = mustGetNode(nodeId);
        ApprovalRecord record = mustGetRecord(node.getRecordId());

        assertRecordPending(record, "通过");
        node.setStatus("APPROVED");
        node.setOpinion(comment);
        node.setApproverActualId(operatorId);
        node.setOperatedAt(LocalDateTime.now());
        approvalNodeMapper.updateById(node);

        // 查找下一节点（node_order 升序第一个 PENDING）
        ApprovalNode next = findNextPendingNode(record.getId(), node.getNodeOrder());
        if (next == null) {
            // 最后节点 → 审批记录状态 APPROVED
            record.setStatus("APPROVED");
            record.setCompletedAt(LocalDateTime.now());
            record.setCurrentNodeName(null);
            record.setCurrentNodeId(null);
            this.updateById(record);
            log.info("审批通过（最后节点）：recordId={}", record.getId());
        } else {
            // 激活下一节点
            next.setStatus("PENDING");
            approvalNodeMapper.updateById(next);
            record.setCurrentNodeName(next.getNodeName());
            this.updateById(record);
            log.info("审批通过，流转下一节点：recordId={}, nextNode={}", record.getId(), next.getNodeName());
        }

        recordHistory(record.getId(), record.getRound(), node.getNodeName(), operatorId,
                null, "APPROVE", comment);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecord reject(Long nodeId, String comment, Long operatorId) {
        ApprovalNode node = mustGetNode(nodeId);
        ApprovalRecord record = mustGetRecord(node.getRecordId());

        assertRecordPending(record, "退回");
        node.setStatus("REJECTED");
        node.setOpinion(comment);
        node.setApproverActualId(operatorId);
        node.setOperatedAt(LocalDateTime.now());
        approvalNodeMapper.updateById(node);

        // 审批记录状态 REJECTED（round 不变，重新提交时 +1）
        record.setStatus("REJECTED");
        record.setCompletedAt(LocalDateTime.now());
        this.updateById(record);

        log.info("审批退回：recordId={}, round={}, comment={}", record.getId(), record.getRound(), comment);
        recordHistory(record.getId(), record.getRound(), node.getNodeName(), operatorId,
                null, "REJECT", comment);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecord withdraw(Long recordId, Long operatorId) {
        ApprovalRecord record = mustGetRecord(recordId);

        assertRecordPending(record, "撤回");
        // 仅提交人可撤回
        if (record.getSubmitterId() == null || !record.getSubmitterId().equals(operatorId)) {
            throw new BusinessException("仅提交人可撤回审批");
        }

        record.setStatus("WITHDRAWN");
        record.setCompletedAt(LocalDateTime.now());
        this.updateById(record);

        log.info("审批撤回：recordId={}, operatorId={}", record.getId(), operatorId);
        recordHistory(record.getId(), record.getRound(), "提交人", operatorId,
                record.getSubmitterName(), "WITHDRAW", null);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecord resubmit(Long recordId, String comment) {
        ApprovalRecord record = mustGetRecord(recordId);

        // 仅 REJECTED/WITHDRAWN 状态可重新提交
        if (!"REJECTED".equals(record.getStatus()) && !"WITHDRAWN".equals(record.getStatus())) {
            throw new BusinessException("仅退回或撤回状态的审批可重新提交，当前状态：" + record.getStatus());
        }

        // 复用原记录：round+1，状态回 PENDING
        int newRound = (record.getRound() == null ? 1 : record.getRound()) + 1;
        record.setRound(newRound);
        record.setStatus("PENDING");
        record.setSubmittedAt(LocalDateTime.now());
        record.setCompletedAt(null);
        this.updateById(record);

        // 重置所有节点为 PENDING，重新从第一个节点开始
        List<ApprovalNode> nodes = listNodes(record.getId());
        for (ApprovalNode n : nodes) {
            n.setStatus("PENDING");
            n.setOpinion(null);
            n.setApproverActualId(null);
            n.setOperatedAt(null);
            approvalNodeMapper.updateById(n);
        }
        // 当前节点指向第一个节点
        if (!nodes.isEmpty()) {
            ApprovalNode firstNode = nodes.stream().min(Comparator.comparingInt(ApprovalNode::getNodeOrder)).orElse(null);
            if (firstNode != null) {
                record.setCurrentNodeName(firstNode.getNodeName());
                this.updateById(record);
            }
        }

        log.info("审批重新提交：recordId={}, newRound={}", record.getId(), newRound);
        recordHistory(record.getId(), newRound, "提交人", record.getSubmitterId(),
                record.getSubmitterName(), "RESUBMIT", comment);
        return record;
    }

    @Override
    public List<ApprovalRecord> listPending(Long userId) {
        // 当前用户作为审批人的节点对应审批记录
        List<ApprovalNode> nodes = approvalNodeMapper.selectList(
                new LambdaQueryWrapper<ApprovalNode>()
                        .eq(ApprovalNode::getApproverId, userId)
                        .eq(ApprovalNode::getStatus, "PENDING"));
        if (nodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> recordIds = nodes.stream().map(ApprovalNode::getRecordId).distinct().toList();
        return this.list(new LambdaQueryWrapper<ApprovalRecord>()
                .in(ApprovalRecord::getId, recordIds)
                .eq(ApprovalRecord::getStatus, "PENDING")
                .orderByDesc(ApprovalRecord::getSubmittedAt));
    }

    @Override
    public List<ApprovalRecord> listSubmitted(Long userId) {
        return this.list(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getSubmitterId, userId)
                .orderByDesc(ApprovalRecord::getSubmittedAt));
    }

    @Override
    public List<ApprovalRecord> listByProject(Long projectId) {
        return this.list(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getProjectId, projectId)
                .orderByDesc(ApprovalRecord::getSubmittedAt));
    }

    @Override
    public List<ApprovalHistory> listHistory(Long recordId) {
        return approvalHistoryMapper.selectList(new LambdaQueryWrapper<ApprovalHistory>()
                .eq(ApprovalHistory::getRecordId, recordId)
                .orderByAsc(ApprovalHistory::getRound)
                .orderByAsc(ApprovalHistory::getOperatedAt));
    }

    @Override
    public ApprovalStatisticsVO statistics(Long userId) {
        long total = this.count(buildStatWrapper(userId, null));
        long pending = this.count(buildStatWrapper(userId, "PENDING"));
        long approved = this.count(buildStatWrapper(userId, "APPROVED"));
        long rejected = this.count(buildStatWrapper(userId, "REJECTED"));
        long withdrawn = this.count(buildStatWrapper(userId, "WITHDRAWN"));
        long timeout = this.count(buildStatWrapper(userId, "TIMEOUT"));
        return ApprovalStatisticsVO.builder()
                .totalCount(total)
                .pendingCount(pending)
                .approvedCount(approved)
                .rejectedCount(rejected)
                .withdrawnCount(withdrawn)
                .timeoutCount(timeout)
                .build();
    }

    // ===================== 内部辅助方法 =====================

    /**
     * 构造统计查询 wrapper：按 submitterId（可选）+ status（可选）过滤。
     */
    private LambdaQueryWrapper<ApprovalRecord> buildStatWrapper(Long userId, String status) {
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(ApprovalRecord::getSubmitterId, userId);
        }
        if (status != null) {
            wrapper.eq(ApprovalRecord::getStatus, status);
        }
        return wrapper;
    }

    private void validateCreate(ApprovalRecord record) {
        if (record.getApprovalType() == null || record.getApprovalType().isBlank()) {
            throw new BusinessException("审批类型不能为空");
        }
        if (record.getBusinessId() == null) {
            throw new BusinessException("业务对象ID不能为空");
        }
        if (record.getTitle() == null || record.getTitle().isBlank()) {
            throw new BusinessException("审批标题不能为空");
        }
        if (record.getSubmitterId() == null) {
            throw new BusinessException("提交人ID不能为空");
        }
    }

    private ApprovalNode mustGetNode(Long nodeId) {
        if (nodeId == null) {
            throw new BusinessException("审批节点ID不能为空");
        }
        ApprovalNode node = approvalNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new BusinessException("审批节点不存在：nodeId=" + nodeId);
        }
        return node;
    }

    private ApprovalRecord mustGetRecord(Long recordId) {
        if (recordId == null) {
            throw new BusinessException("审批记录ID不能为空");
        }
        ApprovalRecord record = this.getById(recordId);
        if (record == null) {
            throw new BusinessException("审批记录不存在：recordId=" + recordId);
        }
        return record;
    }

    private void assertRecordPending(ApprovalRecord record, String action) {
        if (!"PENDING".equals(record.getStatus())) {
            throw new BusinessException("当前审批状态不允许" + action + "：" + record.getStatus());
        }
    }

    private List<ApprovalNode> listNodes(Long recordId) {
        return approvalNodeMapper.selectList(new LambdaQueryWrapper<ApprovalNode>()
                .eq(ApprovalNode::getRecordId, recordId)
                .orderByAsc(ApprovalNode::getNodeOrder));
    }

    private ApprovalNode findNextPendingNode(Long recordId, Integer currentOrder) {
        List<ApprovalNode> nodes = listNodes(recordId);
        return nodes.stream()
                .filter(n -> n.getNodeOrder() != null && n.getNodeOrder() > currentOrder)
                .min(Comparator.comparingInt(ApprovalNode::getNodeOrder))
                .orElse(null);
    }

    private void recordHistory(Long recordId, Integer round, String nodeName,
                              Long operatorId, String operatorName, String action, String opinion) {
        ApprovalHistory history = ApprovalHistory.builder()
                .recordId(recordId)
                .round(round == null ? 1 : round)
                .nodeName(nodeName == null ? "" : nodeName)
                .operatorId(operatorId == null ? 0L : operatorId)
                .operatorName(operatorName)
                .action(action)
                .opinion(opinion)
                .operatedAt(LocalDateTime.now())
                .build();
        approvalHistoryMapper.insert(history);
    }
}
