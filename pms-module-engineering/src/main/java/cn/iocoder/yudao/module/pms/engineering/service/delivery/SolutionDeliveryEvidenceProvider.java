package cn.iocoder.yudao.module.pms.engineering.service.delivery;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 实施方案业务成果证据提供方：登记/重验交付材料时校验方案存在、处于已通过状态（3）、
 * 基线版本与材料修订锚一致。批准归档见 {@code SolutionServiceImpl#archiveApprovedSolution}。
 */
@Component
@RequiredArgsConstructor
public class SolutionDeliveryEvidenceProvider implements DeliveryBusinessObjectEvidenceProvider {

    /** 方案已通过状态（SolutionServiceImpl 状态机：0 草稿→…→3 已通过）。 */
    private static final int STATUS_APPROVED = 3;

    private final SolutionMapper solutionMapper;

    @Override
    public boolean supports(String businessObjectType) {
        return "solution".equals(businessObjectType);
    }

    @Override
    public void validateCurrent(Long tenantId, Long projectId, String businessObjectId, Long businessRevisionNo) {
        SolutionDO solution = solutionMapper.selectById(Long.valueOf(businessObjectId));
        if (solution == null || !tenantId.equals(solution.getTenantId())) {
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID",
                    "实施方案不存在: " + businessObjectId);
        }
        if (!Objects.equals(solution.getStatus(), STATUS_APPROVED)) {
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID",
                    "实施方案未处于已通过状态，不能作为交付证据: " + businessObjectId);
        }
        if (businessRevisionNo != null && !Objects.equals(
                solution.getBaselineVersion() == null ? null : solution.getBaselineVersion().longValue(),
                businessRevisionNo)) {
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID",
                    "实施方案基线版本与材料修订锚不一致: " + businessObjectId);
        }
    }
}
