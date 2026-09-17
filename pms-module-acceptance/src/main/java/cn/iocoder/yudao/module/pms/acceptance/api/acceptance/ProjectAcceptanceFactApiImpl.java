package cn.iocoder.yudao.module.pms.acceptance.api.acceptance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
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

    private final AcceptanceMapper acceptanceMapper;

    @Override
    public boolean existsPassedFinalAcceptance(Long tenantId, Long projectId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(tenant, tenantId) || projectId == null || projectId <= 0) return false;
        List<AcceptanceDO> finalAcceptances = acceptanceMapper.selectList(Wrappers.<AcceptanceDO>lambdaQuery()
                .eq(AcceptanceDO::getProjectId, projectId)
                .eq(AcceptanceDO::getAcceptanceType, ACCEPTANCE_TYPE_FINAL)
                .in(AcceptanceDO::getStatus, List.of(ACCEPTANCE_STATUS_PASSED, ACCEPTANCE_STATUS_ARCHIVED)));
        return finalAcceptances != null && !finalAcceptances.isEmpty();
    }
}
