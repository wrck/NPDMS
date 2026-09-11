package cn.iocoder.yudao.module.pms.cutover.service.customerreference;
import cn.iocoder.yudao.module.pms.project.api.customer.ProjectCustomerReferenceProvider;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.customerreference.CutoverProjectCustomerReferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
@Component
@RequiredArgsConstructor
public class CutoverProjectCustomerReferenceProvider implements ProjectCustomerReferenceProvider {
    private final CutoverProjectCustomerReferenceMapper mapper;
    @Override public Source source() { return Source.CUTOVER; }
    @Override public long countReferences(Query query) {
        if (query == null || query.tenantId() == null || query.projectId() == null
                || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()))
            throw new IllegalArgumentException("项目客户引用查询缺少受信租户或项目");
        return mapper.countReferences(query);
    }
}
