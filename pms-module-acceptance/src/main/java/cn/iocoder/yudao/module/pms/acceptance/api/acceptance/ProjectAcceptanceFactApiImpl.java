package cn.iocoder.yudao.module.pms.acceptance.api.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

/** 项目闭环终验事实查询；tenantId 只取受信调用上下文。 */
@Service
@Validated
@RequiredArgsConstructor
public class ProjectAcceptanceFactApiImpl implements ProjectAcceptanceFactApi {

    /** 与原 ProjectClosureServiceImpl 闭环校验一致的状态语义：3=已通过，5=已归档。 */
    private static final String ACCEPTANCE_TYPE_FINAL = "FINAL";
    private static final int ACCEPTANCE_STATUS_PASSED = 3;
    private static final int ACCEPTANCE_STATUS_ARCHIVED = 5;
    /** 独立验收活动（交付流程 6.3）的完成语义：活动已完成且当前报告有效并通过。 */
    private static final String ACTIVITY_STATUS_COMPLETED = "COMPLETED";
    private static final String REPORT_STATUS_EFFECTIVE = "EFFECTIVE";
    private static final String CONCLUSION_CODE_PASS = "PASS";

    private final AcceptanceMapper acceptanceMapper;
    private final AcceptanceActivityMapper acceptanceActivityMapper;
    private final AcceptanceReportVersionMapper acceptanceReportVersionMapper;

    @Override
    public boolean existsPassedFinalAcceptance(Long tenantId, Long projectId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(tenant, tenantId) || projectId == null || projectId <= 0) return false;
        List<AcceptanceDO> finalAcceptances = acceptanceMapper.selectList(Wrappers.<AcceptanceDO>lambdaQuery()
                .eq(AcceptanceDO::getProjectId, projectId)
                .eq(AcceptanceDO::getAcceptanceType, ACCEPTANCE_TYPE_FINAL)
                .in(AcceptanceDO::getStatus, List.of(ACCEPTANCE_STATUS_PASSED, ACCEPTANCE_STATUS_ARCHIVED)));
        if (finalAcceptances != null && !finalAcceptances.isEmpty()) return true;
        // 独立验收活动链路（acc_acceptance/acc_acceptance_report_version）不落 acc_acceptance_record，
        // 闭环校验需按同一"终验已通过"语义识别该链路的有效通过报告。
        List<AcceptanceActivityDO> activities = acceptanceActivityMapper.selectList(Wrappers.<AcceptanceActivityDO>lambdaQuery()
                .eq(AcceptanceActivityDO::getProjectId, projectId)
                .eq(AcceptanceActivityDO::getAcceptanceType, ACCEPTANCE_TYPE_FINAL)
                .eq(AcceptanceActivityDO::getActivityStatus, ACTIVITY_STATUS_COMPLETED)
                .isNotNull(AcceptanceActivityDO::getCurrentReportVersionId));
        for (AcceptanceActivityDO activity : activities) {
            AcceptanceReportVersionDO report = acceptanceReportVersionMapper.selectById(activity.getCurrentReportVersionId());
            if (report != null && REPORT_STATUS_EFFECTIVE.equals(report.getReportStatus())
                    && CONCLUSION_CODE_PASS.equals(report.getConclusionCode())) return true;
        }
        return false;
    }
}
