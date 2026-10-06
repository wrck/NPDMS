package cn.iocoder.yudao.module.pms.engineering.service.arrival;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalMapper;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 到货签收单附件的文档来源：登记到货保存后上传的签收单按统一交付清单定义
 * 自动归集到 D_ARRIVAL（automaticSources 授权后由文档归集管道承接）。
 */
@Component
@RequiredArgsConstructor
public class ArrivalDocumentSources implements FileDocumentSourceProvider {

    public static final String SOURCE_CODE = "IMP.ARRIVAL_SIGN_DOCUMENT";

    private final ArrivalMapper arrivalMapper;

    @Override
    public List<Descriptor> descriptors() {
        return List.of(new Descriptor(SOURCE_CODE, "到货签收单附件"));
    }

    @Override
    public Scope resolve(Long tenantId, String ownerContext, String objectType, String objectId,
                         String purposeCode) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())
                || !ArrivalFilePolicyProvider.OWNER_CONTEXT.equals(ownerContext)
                || !ArrivalFilePolicyProvider.OBJECT_TYPE.equals(objectType)
                || !ArrivalFilePolicyProvider.PURPOSE_CODE.equals(purposeCode)) {
            return null;
        }
        Long arrivalId;
        try {
            arrivalId = Long.valueOf(objectId);
        } catch (RuntimeException invalid) {
            return null;
        }
        var arrival = arrivalMapper.selectById(arrivalId);
        if (arrival == null || !Objects.equals(tenantId, arrival.getTenantId())) return null;
        return new Scope(arrival.getProjectId(), SOURCE_CODE, "IMP", "arrival", arrival.getId(), null);
    }
}
