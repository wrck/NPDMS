package cn.iocoder.yudao.module.pms.engineering.service.installation;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationAddressImportApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** Superseded task path: new writes must use the reviewed formal installation mapping. */
@Service
public class DppmsInstallationAddressImportService implements DppmsInstallationAddressImportApi {
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<NativeEntityMigrationRow> importAddresses(Command command) {
        if(command==null || !Objects.equals(command.tenantId(),TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DPPMS_NATIVE_TENANT_MISMATCH");
        if(command.addresses().isEmpty())return List.of();
        throw new IllegalArgumentException("旧安装候选与来源事实路径已撤回，请使用已裁决正式安装迁移协议");
    }
}
