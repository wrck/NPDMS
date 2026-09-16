package cn.iocoder.yudao.module.pms.workflow.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.iocoder.yudao.module.pms.platform.api.spi.dto.ApprovalViolation;
import cn.iocoder.yudao.module.pms.platform.api.spi.ApprovalStatusChecker;
import cn.iocoder.yudao.module.pms.workflow.entity.ApprovalRecord;
import cn.iocoder.yudao.module.pms.workflow.mapper.ApprovalRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 审批状态校验 SPI 实现（TD-P8-005）。
 *
 * <p>实现 {@link ApprovalStatusChecker}，供 {@code pms-project} 的 {@code validateExitGate}
 * APPROVAL 分支跨模块查询关联审批是否已通过。</p>
 *
 * <p>判定逻辑：查询 {@code wf_approval_record} 中 {@code projectId = ?} 且
 * {@code approvalType = ?} 的最新一条记录：
 * <ul>
 *   <li>若 {@code mustApproved=true} 且记录状态为 APPROVED → 返回空列表（通过）</li>
 *   <li>若 {@code mustApproved=true} 且记录不存在或非 APPROVED → 返回违规</li>
 *   <li>若 {@code mustApproved=false} → 返回空列表（不要求）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalStatusCheckerImpl implements ApprovalStatusChecker {

    private static final String STATUS_APPROVED = "APPROVED";
    private static final String EXPECTED_STATUS = "APPROVED";

    private final ApprovalRecordMapper approvalRecordMapper;

    @Override
    public List<ApprovalViolation> findApprovalViolations(Long projectId, String approvalType, boolean mustApproved) {
        if (!mustApproved) {
            return Collections.emptyList();
        }
        if (projectId == null || approvalType == null) {
            return Collections.emptyList();
        }
        // 查询该项目+审批类型下的最新一条审批记录（按 id 倒序取最新）
        ApprovalRecord record = approvalRecordMapper.selectOne(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getProjectId, projectId)
                .eq(ApprovalRecord::getApprovalType, approvalType)
                .orderByDesc(ApprovalRecord::getId)
                .last("LIMIT 1"));
        if (record != null && STATUS_APPROVED.equals(record.getStatus())) {
            return Collections.emptyList();
        }
        // 未通过或不存在 → 违规
        String actualStatus = record != null ? record.getStatus() : null;
        log.info("审批状态校验：projectId={} approvalType={} mustApproved={} actual={}",
                projectId, approvalType, mustApproved, actualStatus);
        return List.of(ApprovalViolation.builder()
                .approvalRecordId(record != null ? record.getId() : null)
                .approvalType(approvalType)
                .expectedStatus(EXPECTED_STATUS)
                .actualStatus(actualStatus)
                .build());
    }
}
