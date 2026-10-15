package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 读取当前正式计划的冻结规则；不回写建项快照或历史计划。 */
@Component
@RequiredArgsConstructor
public class NormalClosurePolicyResolver {
    private final ProjectPlanVersionMapper plans;

    public record Resolution(String policyJson, Long planVersionId, Integer revisionNo) { }

    public Resolution resolve(ProjectMasterDO project) {
        if (project.getActivePlanVersionId() == null) {
            return new Resolution(project.getClosurePolicySnapshot(), null, null);
        }
        var plan = plans.selectById(project.getActivePlanVersionId());
        if (plan == null || !Objects.equals(plan.getTenantId(), project.getTenantId())
                || !Objects.equals(plan.getProjectId(), project.getId())
                || !"EFFECTIVE".equals(plan.getStatus())) {
            throw new cn.iocoder.yudao.framework.common.exception.ServiceException(
                    cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_PROJECT_CLOSURE_VALIDATION_FAILED.getCode(),
                    "CLOSURE_EFFECTIVE_PLAN_REQUIRED");
        }
        var policy = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot()).getClosurePolicy();
        return new Resolution(policy == null || policy.isNull() ? null : JsonUtils.toJsonString(policy),
                plan.getId(), plan.getRevisionNo());
    }
}
