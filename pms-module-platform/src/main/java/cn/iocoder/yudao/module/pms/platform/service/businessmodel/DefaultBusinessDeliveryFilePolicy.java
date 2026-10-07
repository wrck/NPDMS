package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeclaredBusinessDeliveryBridge;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

/** One default file policy for every declared ordinary business; no per-business adapter. */
@Component @RequiredArgsConstructor
public class DefaultBusinessDeliveryFilePolicy implements FileBusinessObjectPolicyProvider {
    private final DeclaredBusinessDeliveryBridge entities;
    private final BusinessCallerContext callers;
    private static final Set<String> MEDIA=Set.of("text/plain","text/csv","application/pdf","image/png","image/jpeg",
            "application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation");
    @Override public String ownerContext(){return "PLT";}
    @Override public String objectType(){return DefaultBusinessDeliveryService.FILE_OBJECT_TYPE;}
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query){return require(query,false,null);}
    @Override public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query){return require(query.toInspectionQuery(),true,query.expectedScopeVersion());}
    @Override public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query){
        return inspect(new FileBusinessObjectPolicyQuery(query.tenantId(),query.actorUserId(),query.key().ownerContext(),query.key().objectType(),
                query.key().objectId(),query.key().purposeCode(),"set",query.requiredAction(),query.ownerExecutionContext()));
    }
    @Override public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery query){
        return require(new FileBusinessObjectPolicyQuery(query.tenantId(),query.actorUserId(),query.key().ownerContext(),query.key().objectType(),
                query.key().objectId(),query.key().purposeCode(),"set",query.requiredAction(),query.ownerExecutionContext()),true,query.expectedScopeVersion());
    }
    private FileBusinessObjectPolicyFact require(FileBusinessObjectPolicyQuery query,boolean lock,Long expectedScope) {
        var caller=callers.require();
        if(!caller.tenantId().equals(query.tenantId()) || !caller.userId().equals(query.actorUserId())
                || !ownerContext().equals(query.ownerContext()) || !objectType().equals(query.objectType())
                || !Set.of(FileActionCodes.UPLOAD,FileActionCodes.READ,FileActionCodes.DOWNLOAD,FileActionCodes.PREVIEW).contains(query.requiredAction()))
            throw new BusinessContractException("ACCESS_DENIED","文件调用上下文或动作不合法");
        DefaultBusinessDeliveryService.type(query.purposeCode());
        var owner=query.objectId()==null?new String[0]:query.objectId().split(":",4);
        if(owner.length!=4) throw new BusinessContractException("ACCESS_DENIED","文件业务身份不合法");
        Long scope=entities.requireDefault(query.tenantId(),query.actorUserId(),owner[1],owner[2],
                DefaultBusinessDeliveryService.entityId(owner[3]),FileActionCodes.UPLOAD.equals(query.requiredAction()),lock,expectedScope);
        if(!DefaultBusinessDeliveryService.entityId(owner[0]).equals(entities.projectId(query.tenantId(),owner[1],owner[2],DefaultBusinessDeliveryService.entityId(owner[3]))))
            throw new BusinessContractException("ACCESS_DENIED","文件业务实体已不属于原项目");
        return new FileBusinessObjectPolicyFact(true,scope,"IMMUTABLE","MULTIPLE",Set.of("DOCUMENT"),MEDIA,52_428_800L,"INTERNAL");
    }
}
