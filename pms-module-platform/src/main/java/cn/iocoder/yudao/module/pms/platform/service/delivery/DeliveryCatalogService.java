package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryCapabilityConfigDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryCapabilityConfigMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryTypeMapper;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 统一交付类型目录与实体适用配置。类型停用后拒绝新登记/新提交，既有要求与材料不受影响；
 * 适用配置变更只影响此后新建的要求实例，不改写既有要求的口径。
 */
@Service
@RequiredArgsConstructor
public class DeliveryCatalogService {

    private final DeliveryTypeMapper typeMapper;
    private final DeliveryCapabilityConfigMapper configMapper;

    @Transactional
    public DeliveryTypeDO createType(String typeCode, String name, String category,
                                     List<String> allowedMediaTypes, Long maxSizeBytes, String remark) {
        if (allowedMediaTypes == null || allowedMediaTypes.isEmpty()) {
            throw new BusinessContractException("CONTRACT_REJECTED", "交付类型必须声明允许的媒体类型");
        }
        if (maxSizeBytes == null || maxSizeBytes <= 0) {
            throw new BusinessContractException("CONTRACT_REJECTED", "交付类型必须声明正数大小上限");
        }
        typeMapper.selectByCode(typeCode).ifPresent(existing -> {
            throw new BusinessContractException("DELIVERY_TYPE_EXISTS", "交付类型编码已存在: " + typeCode);
        });
        DeliveryTypeDO row = new DeliveryTypeDO();
        row.setTypeCode(typeCode);
        row.setName(name);
        row.setCategory(category);
        row.setAllowedMediaJson(JsonUtils.toJsonString(allowedMediaTypes));
        row.setMaxSizeBytes(maxSizeBytes);
        row.setEnabled(true);
        row.setRemark(remark);
        typeMapper.insert(row);
        return row;
    }

    @Transactional
    public DeliveryTypeDO updateType(Long id, String name, String category, List<String> allowedMediaTypes,
                                     Long maxSizeBytes, Boolean enabled, String remark) {
        DeliveryTypeDO row = typeMapper.selectById(id);
        if (row == null) {
            throw new BusinessContractException("DELIVERY_TYPE_NOT_FOUND", "交付类型不存在: " + id);
        }
        if (allowedMediaTypes != null && !allowedMediaTypes.isEmpty()) {
            row.setAllowedMediaJson(JsonUtils.toJsonString(allowedMediaTypes));
        }
        if (maxSizeBytes != null && maxSizeBytes > 0) {
            row.setMaxSizeBytes(maxSizeBytes);
        }
        if (name != null && !name.isBlank()) {
            row.setName(name);
        }
        if (category != null && !category.isBlank()) {
            row.setCategory(category);
        }
        if (remark != null) {
            row.setRemark(remark);
        }
        if (enabled != null) {
            row.setEnabled(enabled);
        }
        typeMapper.updateById(row);
        return row;
    }

    public List<DeliveryTypeDO> listTypes(Boolean enabled) {
        return typeMapper.selectAll().stream()
                .filter(row -> enabled == null || row.getEnabled().equals(enabled))
                .toList();
    }

    public DeliveryTypeDO requireType(String typeCode) {
        return typeMapper.selectByCode(typeCode).orElseThrow(() -> new BusinessContractException(
                "DELIVERY_TYPE_NOT_FOUND", "交付类型不存在: " + typeCode));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public DeliveryTypeDO lockEnabledType(String typeCode) {
        var row = typeMapper.selectCodeForUpdate(new cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DeliveryTypeCodeLockQuery(
                cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(),typeCode));
        if (row == null) throw new BusinessContractException("DELIVERY_TYPE_NOT_FOUND", "交付类型不存在");
        if (!Boolean.TRUE.equals(row.getEnabled())) throw new BusinessContractException("DELIVERY_TYPE_DISABLED", "交付类型已停用");
        return row;
    }

    public DeliveryTypeDO requireEnabledType(String typeCode) {
        DeliveryTypeDO row = requireType(typeCode);
        if (!Boolean.TRUE.equals(row.getEnabled())) {
            throw new BusinessContractException("DELIVERY_TYPE_DISABLED", "交付类型已停用: " + typeCode);
        }
        return row;
    }

    /** 可空查找：purposeCode 非类型目录编码（模板冻结交付件编码等）时返回 null，由策略提供方委派 Owner 校验。 */
    public DeliveryTypeDO findEnabledType(String typeCode) {
        return typeMapper.selectByCode(typeCode)
                .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                .orElse(null);
    }

    public List<String> allowedMedia(DeliveryTypeDO type) {
        return JsonUtils.parseObject(type.getAllowedMediaJson(),
                new tools.jackson.core.type.TypeReference<List<String>>() {});
    }

    @Transactional
    public DeliveryCapabilityConfigDO upsertConfig(String ownerModule, String entityType, String typeCode,
                                                   Boolean required, Integer minimumQuantity,
                                                   String countingUnit, Boolean enabled) {
        requireEnabledType(typeCode);
        validateCountingUnit(countingUnit);
        List<DeliveryCapabilityConfigDO> existing = configMapper.selectByEntity(ownerModule, entityType).stream()
                .filter(row -> row.getTypeCode().equals(typeCode))
                .toList();
        if (!existing.isEmpty()) {
            DeliveryCapabilityConfigDO row = existing.getFirst();
            row.setRequired(required != null ? required : row.getRequired());
            row.setMinimumQuantity(minimumQuantity != null ? minimumQuantity : row.getMinimumQuantity());
            row.setCountingUnit(countingUnit != null ? countingUnit : row.getCountingUnit());
            row.setEnabled(enabled != null ? enabled : row.getEnabled());
            configMapper.updateById(row);
            return row;
        }
        DeliveryCapabilityConfigDO row = new DeliveryCapabilityConfigDO();
        row.setOwnerModule(ownerModule);
        row.setEntityType(entityType);
        row.setTypeCode(typeCode);
        row.setRequired(required != null && required);
        row.setMinimumQuantity(minimumQuantity != null ? minimumQuantity : 0);
        row.setCountingUnit(countingUnit);
        row.setEnabled(enabled == null || enabled);
        configMapper.insert(row);
        return row;
    }

    public static void validateCountingUnit(String countingUnit) {
        if (!Set.of(DeliveryTypeDO.COUNTING_MATERIAL, DeliveryTypeDO.COUNTING_FILE_VERSION,
                DeliveryTypeDO.COUNTING_SUBMISSION).contains(countingUnit)) {
            throw new BusinessContractException("CONTRACT_REJECTED", "不支持的计数单位: " + countingUnit);
        }
    }

    public List<DeliveryCapabilityConfigDO> listConfigs(String ownerModule, String entityType) {
        return configMapper.selectByEntity(ownerModule, entityType);
    }

    public List<DeliveryCapabilityConfigDO> enabledConfigs(String ownerModule, String entityType) {
        return configMapper.selectByEntity(ownerModule, entityType).stream()
                .filter(row -> Boolean.TRUE.equals(row.getEnabled()))
                .toList();
    }
}
