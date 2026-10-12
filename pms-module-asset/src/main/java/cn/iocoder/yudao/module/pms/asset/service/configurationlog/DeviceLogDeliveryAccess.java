package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

/** Material access delegates to the immutable log Owner's existing file policy. */
@Component @RequiredArgsConstructor
public class DeviceLogDeliveryAccess implements DeliveryMaterialUploadPolicyValidator {
    private final DeviceLogFilePolicyProvider logs;
    @Override public String ownerModule() { return "AST"; }
    @Override public boolean supportsEntityType(String type) { return "device-log-file".equals(type); }
    @Override public boolean allowsGenericDeliveryActions(String type) { return false; }
    @Override public Long requireDeliveryAccess(Long tenant,Long actor,String type,String id,String purpose,
                                               boolean write,boolean lock,Long expectedScope) {
        if(write) throw denied();
        return validateUpload(tenant,actor,type,id,purpose,"DOWNLOAD",lock,expectedScope).scopeVersion();
    }
    @Override public FileBusinessObjectPolicyFact validateUpload(Long tenant,Long actor,String type,String id,
                                                                String purpose,String action,boolean lock,Long expectedScope) {
        if(!supportsEntityType(type) || !Set.of(DeviceLogDocumentSources.SOURCE,"AST.DEVICE_CONFIGURATION_LOG_SOURCE").contains(purpose)
                || !Set.of("READ","DOWNLOAD","PREVIEW").contains(action)) throw denied();
        var query=new FileBusinessObjectPolicyQuery(tenant,actor,"AST",DeviceLogFilePolicyProvider.OBJECT,id,
                DeviceLogFilePolicyProvider.PURPOSE,"device-log-file:"+id,action);
        var initial=logs.inspect(query);
        if(initial==null || !initial.allowed() || initial.scopeVersion()==null
                || expectedScope!=null && !expectedScope.equals(initial.scopeVersion())) throw denied();
        if(!lock) return initial;
        var current=logs.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(tenant,actor,"AST",
                DeviceLogFilePolicyProvider.OBJECT,id,DeviceLogFilePolicyProvider.PURPOSE,"device-log-file:"+id,action,initial.scopeVersion()));
        if(current==null || !current.allowed() || !initial.scopeVersion().equals(current.scopeVersion())) throw denied();
        return current;
    }
    private BusinessContractException denied() { return new BusinessContractException("DELIVERY_ACCESS_DENIED","无设备日志材料读取权限或范围已变化"); }
}
