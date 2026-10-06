package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryBusinessObjectEvidenceProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultEvidenceApi;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 项目业务成果证据提供方（P06R I2，SPI）：BUSINESS_RESULT 材料的 businessObjectType 固定为
 * {@link #BUSINESS_OBJECT_TYPE}，businessObjectId 为 `type|objectId|resultId|formedAt` 复合身份
 * （type = ownerContext.entityType.resultType）；登记与重验时按成果类型描述符锁定重查，
 * 校验成果仍为 CURRENT 且身份与修订锚一致。复合构造器供提交路径共用。
 */
@Component
public class ProjectDeliverableBusinessResultEvidenceProvider implements DeliveryBusinessObjectEvidenceProvider {

    public static final String BUSINESS_OBJECT_TYPE = "project_business_result";

    /** 复合身份 formedAt 段固定秒级 ISO 格式；提交与迁移共用，避免本地化序列化差异。 */
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ProjectBusinessResultEvidenceApi results;

    public ProjectDeliverableBusinessResultEvidenceProvider(ProjectBusinessResultEvidenceApi results) {
        this.results = results;
    }

    @Override public Identity identity(Long tenantId,Long projectId,String objectId,Long revision) {
        validateCurrent(tenantId,projectId,objectId,revision);
        String[] parts=objectId.split("\\|",4);
        var descriptor=results.types().stream().filter(value->typeCode(value.type()).equals(parts[0])).findFirst().orElseThrow();
        var identity=results.deliveryIdentity(new BusinessResultSource.Query(tenantId,projectId,descriptor.type(),parts[1],descriptor.exactLookup()?parts[2]:null));
        return identity==null?null:new Identity(identity.ownerModule(),identity.entityType(),identity.entityId(),identity.businessTypeCode(),
                identity.businessObjectType(),identity.businessObjectId(),identity.businessRevisionNo());
    }

    @Override
    public boolean supports(String businessObjectType) {
        return BUSINESS_OBJECT_TYPE.equals(businessObjectType);
    }

    @Override
    public void validateCurrent(Long tenantId, Long projectId, String businessObjectId, Long businessRevisionNo) {
        String[] parts = businessObjectId == null ? new String[0] : businessObjectId.split("\\|", 4);
        if (parts.length != 4 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw invalid("业务成果材料标识无效");
        }
        var descriptor = results.types().stream()
                .filter(value -> typeCode(value.type()).equals(parts[0]))
                .findFirst()
                .orElseThrow(() -> invalid("不支持的业务成果类型: " + parts[0]));
        var observation = results.lockAndInspect(new BusinessResultSource.Query(tenantId, projectId,
                descriptor.type(), parts[1], descriptor.exactLookup() ? parts[2] : null));
        var result = observation.result();
        if (result == null || result.validity() != BusinessResultSource.Validity.CURRENT
                || !parts[2].equals(result.resultId()) || !Objects.equals(parts[3],
                        result.formedAt() == null ? "" : FORMATTER.format(result.formedAt()))) {
            throw invalid("业务成果尚未形成、已撤销或已被替换");
        }
        if (businessRevisionNo != null && !String.valueOf(businessRevisionNo).equals(result.businessRevision())) {
            throw invalid("业务成果修订已变化");
        }
    }

    /** 提交路径与迁移共用：业务成果复合身份（formedAt 为空时该段留空）。 */
    public static String compositeObjectId(BusinessResultSource.Type type, String objectId,
                                           String resultId, LocalDateTime formedAt) {
        return typeCode(type) + "|" + objectId + "|" + resultId + "|"
                + (formedAt == null ? "" : FORMATTER.format(formedAt));
    }

    public static String typeCode(BusinessResultSource.Type type) {
        return type.ownerContext() + "." + type.entityType() + "." + type.resultType();
    }

    private static BusinessContractException invalid(String message) {
        return new BusinessContractException("DELIVERY_BUSINESS_OBJECT_INVALID", message);
    }
}
