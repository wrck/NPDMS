package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsLocalContractSourceApi;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.DppmsLocalContractSourceMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class DppmsLocalContractSourceService implements DppmsLocalContractSourceApi {
    private final DppmsLocalContractSourceMapper mapper;
    private final PermissionApi permissions;
    private final AdminUserApi users;

    @Override @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Contract> read(Query query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.actorId()==null || query.actorId()<=0 || query.contractNumbers()==null
                || query.contractNumbers().stream().anyMatch(s->s==null || s.isBlank())
                || query.maxBytes()<1 || query.maxBytes()>64L*1024*1024)
            throw new IllegalArgumentException("DPPMS_LOCAL_CONTRACT_SOURCE_INVALID");
        users.validateUser(query.actorId());
        if(!permissions.hasAnyPermissions(query.actorId(),"pms:integration:execute"))
            throw new org.springframework.security.access.AccessDeniedException("缺少迁移执行权限");
        // Read the full local snapshot before INT writes contracts. No target work joins this read-only transaction.
        var result=new ArrayList<Contract>();String company=null,contract=null;long bytes=0;
        while(true) {
            var page=mapper.selectPage(new DppmsLocalContractSourceMapper.Page(query.tenantId(),query.contractNumbers(),company,contract));
            if(page.isEmpty())break;
            for(var row:page) {
                bytes+=JsonUtils.toJsonString(row).getBytes(StandardCharsets.UTF_8).length;
                if(bytes>query.maxBytes())throw new IllegalArgumentException("本地合同来源超过快照容量，未写入目标");
                result.add(row);
            }
            company=page.getLast().companyCode();contract=page.getLast().contractNo();
        }
        return List.copyOf(result);
    }
}
