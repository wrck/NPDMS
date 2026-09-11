package cn.iocoder.yudao.module.pms.platform.service.customerreference;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.customerreference.PlatformProjectCustomerReferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
@Component
@RequiredArgsConstructor
public class PlatformProjectCustomerReferenceProvider implements ProjectCustomerReferenceProvider {
    private final PlatformProjectCustomerReferenceMapper mapper;
    @Override public Source source() { return Source.PLATFORM; }
    @Override public long countReferences(Query query) {
        if (query == null || query.tenantId() == null || query.projectId() == null
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()))
            throw new IllegalArgumentException("项目客户引用查询缺少受信租户或项目");
        return mapper.countReferences(new PlatformProjectCustomerReferenceMapper.Query(query.tenantId(), String.valueOf(query.projectId())));
    }
}
