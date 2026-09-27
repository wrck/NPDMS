package cn.iocoder.yudao.module.pms.platform.service.businessmodel.fact;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection.BusinessCollectionPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection.BusinessCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.BusinessFactPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalAttemptDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryRequirementDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.approval.ApprovalAttemptMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryRequirementMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryRequirementService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 公共事实读取默认实现：字段事实经标准受控访问以系统观察者身份读取；
 * 关系成员经集合访问端口漏页穷举（漏页或成员不可读一律 PARTIAL/UNKNOWN）；
 * 交付件与审批事实按各自公共能力表计数。未知、已知空值和不满足分立表达，不伪造满足。
 */
@Component
public class DefaultBusinessFactPort implements BusinessFactPort {

    private static final String SOURCE = "unified-access";
    private static final int MEMBER_PAGE_SIZE = 200;

    private final BusinessEntityAccessPort accessPort;
    private final BusinessCollectionPort collectionPort;
    private final DeliveryMaterialMapper deliveryMaterialMapper;
    private final DeliveryRequirementMapper deliveryRequirementMapper;
    private final DeliveryRequirementService deliveryRequirementService;
    private final ApprovalAttemptMapper approvalAttemptMapper;

    public DefaultBusinessFactPort(BusinessEntityAccessPort accessPortBean,
                                   DeliveryMaterialMapper deliveryMaterialMapper,
                                   DeliveryRequirementMapper deliveryRequirementMapper,
                                   DeliveryRequirementService deliveryRequirementService,
                                   ApprovalAttemptMapper approvalAttemptMapper) {
        // 默认访问实现同时承载集合端口；与 BusinessModelAccessConfiguration 的装配方式一致。
        this.accessPort = accessPortBean;
        this.collectionPort = (BusinessCollectionPort) accessPortBean;
        this.deliveryMaterialMapper = deliveryMaterialMapper;
        this.deliveryRequirementMapper = deliveryRequirementMapper;
        this.deliveryRequirementService = deliveryRequirementService;
        this.approvalAttemptMapper = approvalAttemptMapper;
    }

    @Override
    public FactObservation observeField(EntityDataRef ref, String fieldCode, String sceneCode) {
        try {
            EntityActor observer = new EntityActor(ref.entity().tenantId(), 0L, EntityActor.SYSTEM_OBSERVER);
            BusinessEntityData data = accessPort.read(ref, observer, sceneCode);
            if (!data.available()) {
                return new FactObservation(SOURCE, FactObservation.Availability.UNAVAILABLE,
                        null, data.unavailableReason(), Completeness.COMPLETE);
            }
            Object value = data.fieldValues().get(fieldCode);
            if (value == null) {
                return new FactObservation(SOURCE, FactObservation.Availability.EMPTY,
                        null, "字段值为空: " + fieldCode, Completeness.COMPLETE);
            }
            return new FactObservation(SOURCE, FactObservation.Availability.VALUE,
                    value, "当前对象读取", Completeness.COMPLETE);
        } catch (Exception ex) {
            return new FactObservation(SOURCE, FactObservation.Availability.UNAVAILABLE,
                    null, ex.getMessage(), Completeness.COMPLETE);
        }
    }

    @Override
    public FactObservation observeMembers(String ownerModule, String entityType, Long entityId,
                                          String relationCode, List<BusinessFieldFilter> scopeFilters,
                                          String sceneCode) {
        try {
            Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
            if (tenantId == null) {
                return unavailable("租户上下文缺失，无法以系统观察者读取关系成员");
            }
            EntityActor observer = new EntityActor(tenantId, 0L, EntityActor.SYSTEM_OBSERVER);
            // 关系成员必须漏页穷举；分页第一页不能冒充全集。
            Set<Long> memberIds = new HashSet<>();
            String cursor = null;
            boolean exhausted = false;
            int guards = 0;
            while (!exhausted) {
                if (++guards > 10_000) {
                    return unavailable("关系成员枚举超过安全页数上限");
                }
                var slice = collectionPort.members(new BusinessCollectionQuery(ownerModule, entityType,
                        entityId, relationCode, scopeFilters, MEMBER_PAGE_SIZE, cursor), observer);
                if (slice.completeness() == Completeness.UNAVAILABLE) {
                    return unavailable(slice.unavailableReason() == null ? "关系成员不可读" : slice.unavailableReason());
                }
                for (BusinessEntityData member : slice.members()) {
                    if (!member.available()) {
                        return new FactObservation(SOURCE, FactObservation.Availability.UNKNOWN,
                                null, "成员不可读: " + member.unavailableReason(), Completeness.PARTIAL);
                    }
                    memberIds.add(member.ref().entityId());
                }
                exhausted = slice.nextCursor() == null;
                cursor = slice.nextCursor();
            }
            return countObservation("关系成员计数: " + relationCode, memberIds.size(), true);
        } catch (BusinessContractException ex) {
            return unavailable(ex.getMessage());
        } catch (Exception ex) {
            return new FactObservation(SOURCE, FactObservation.Availability.UNKNOWN,
                    null, "关系成员观察失败: " + ex.getMessage(), Completeness.PARTIAL);
        }
    }

    @Override
    public FactObservation observeDelivery(EntityRef entity, String sceneCode) {
        try {
            List<DeliveryMaterialDO> materials = deliveryMaterialMapper
                    .selectByEntity(entity.ownerModule(), entity.entityType(), entity.entityId(), null)
                    .stream().filter(item -> DeliveryMaterialDO.STATUS_ACTIVE.equals(item.getStatus()))
                    .toList();
            List<DeliveryRequirementDO> requirements = deliveryRequirementService
                    .viewByEntity(entity.ownerModule(), entity.entityType(), entity.entityId()).stream()
                    .map(DeliveryRequirementService.RequirementView::requirement).toList();
            long satisfied = requirements.stream()
                    .filter(item -> DeliveryRequirementDO.STATUS_SATISFIED.equals(item.getStatus())
                            || DeliveryRequirementDO.STATUS_CONFIRMED.equals(item.getStatus()))
                    .count();
            long gap = requirements.size() - satisfied;
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("materialCount", materials.size());
            value.put("satisfiedRequirementCount", satisfied);
            value.put("requirementGap", gap);
            return new FactObservation(SOURCE, FactObservation.Availability.VALUE, value,
                    "交付件事实计数", Completeness.COMPLETE);
        } catch (Exception ex) {
            return unavailable("交付件事实读取失败: " + ex.getMessage());
        }
    }

    @Override
    public FactObservation observeApproval(EntityRef entity, String sceneCode) {
        try {
            List<ApprovalAttemptDO> attempts = approvalAttemptMapper.selectBySubject(
                    entity.ownerModule(), entity.entityType(), entity.entityId(), null);
            long pending = attempts.stream()
                    .filter(item -> ApprovalAttemptDO.STATUS_PENDING.equals(item.getStatus())).count();
            long approved = attempts.stream()
                    .filter(item -> ApprovalAttemptDO.STATUS_APPROVED.equals(item.getStatus())).count();
            long rejected = attempts.stream()
                    .filter(item -> ApprovalAttemptDO.STATUS_REJECTED.equals(item.getStatus())).count();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("pendingCount", pending);
            value.put("approvedCount", approved);
            value.put("rejectedCount", rejected);
            return new FactObservation(SOURCE, FactObservation.Availability.VALUE, value,
                    "审批事实计数", Completeness.COMPLETE);
        } catch (Exception ex) {
            return unavailable("审批事实读取失败: " + ex.getMessage());
        }
    }

    private static FactObservation countObservation(String basis, int count, boolean complete) {
        if (count == 0) {
            return new FactObservation(SOURCE, FactObservation.Availability.EMPTY, 0L,
                    basis, complete ? Completeness.COMPLETE : Completeness.PARTIAL);
        }
        return new FactObservation(SOURCE, FactObservation.Availability.VALUE, (long) count,
                basis, complete ? Completeness.COMPLETE : Completeness.PARTIAL);
    }

    private static FactObservation unavailable(String reason) {
        return new FactObservation(SOURCE, FactObservation.Availability.UNAVAILABLE,
                null, reason, Completeness.UNAVAILABLE);
    }
}
