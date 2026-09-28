package cn.iocoder.yudao.module.pms.engineering.service.delivery;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.engineering.enums.TrainingStatusEnum;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 现场培训业务成果证据提供方：登记/重验交付材料时校验培训存在、处于客户已确认状态
 * （ACC-01，客户签认后归档）。培训无版本修订，修订锚恒为空。
 */
@Component
@RequiredArgsConstructor
public class TrainingDeliveryEvidenceProvider implements DeliveryBusinessObjectEvidenceProvider {

    private final TrainingMapper trainingMapper;

    @Override
    public boolean supports(String businessObjectType) {
        return "training".equals(businessObjectType);
    }

    @Override
    public void validateCurrent(Long tenantId, Long projectId, String businessObjectId, Long businessRevisionNo) {
        TrainingDO training = trainingMapper.selectById(Long.valueOf(businessObjectId));
        if (training == null || !tenantId.equals(training.getTenantId())) {
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID",
                    "现场培训记录不存在: " + businessObjectId);
        }
        if (!Objects.equals(training.getStatus(), TrainingStatusEnum.CONFIRMED.getStatus())) {
            throw new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID",
                    "现场培训未处于客户已确认状态，不能作为交付证据: " + businessObjectId);
        }
    }
}
