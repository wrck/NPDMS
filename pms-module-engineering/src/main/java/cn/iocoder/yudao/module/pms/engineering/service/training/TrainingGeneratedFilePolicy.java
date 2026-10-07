package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.TrainingFileOwnerQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Native generated snapshots only. Public mutating file actions are intentionally unavailable. */
@Component @RequiredArgsConstructor
public class TrainingGeneratedFilePolicy implements FileBusinessObjectPolicyProvider,FileDocumentSourceProvider {
    private final TrainingMapper training;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    private final ProjectAcceptanceContextApi projects;
    @Override public String ownerContext(){return "IMP";}
    @Override public String objectType(){return "TRAINING_RECORD";}
    @Override public List<Descriptor> descriptors(){return List.of(new Descriptor("TRAINING_RECORD","Training record"));}
    private static boolean purpose(String value){return value!=null && value.matches("TRAINING_RECORD_(HTML|PDF)/[0-9]+");}
    private cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO locate(Long tenant,String id,boolean lock){
        if(!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()))return null;
        Long key;try{key=Long.valueOf(id);}catch(RuntimeException invalid){return null;}
        if(key<=0)return null;
        var row=lock?training.selectFileOwnerForUpdate(new TrainingFileOwnerQuery(tenant,key)):training.selectById(key);
        return row!=null && Objects.equals(tenant,row.getTenantId())?row:null;
    }
    @Override public Scope resolve(Long tenant,String owner,String type,String id,String purpose){
        if(!"IMP".equals(owner)||!"TRAINING_RECORD".equals(type)||!purpose(purpose))return null;
        var row=locate(tenant,id,false);
        return row==null?null:new Scope(row.getProjectId(),"TRAINING_RECORD","IMP","training",row.getId(),null);
    }
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query){
        if(!Set.of(FileActionCodes.READ,FileActionCodes.DOWNLOAD,FileActionCodes.PREVIEW).contains(query.requiredAction())||!purpose(query.purposeCode()))return denied();
        var row=locate(query.tenantId(),query.objectId(),false);
        if(row==null||!permissions.hasAnyPermissions(query.actorUserId(),"pms:imp-training:query"))return denied();
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(),query.actorUserId(),row.getProjectId(),ProjectScopeApi.ACTION_VIEW));
        return visible(scope,row.getProjectId())?fact(scope.treeVersion()):denied();
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query){
        var inspected=inspect(query.toInspectionQuery());if(!inspected.allowed())return inspected;
        var row=locate(query.tenantId(),query.objectId(),true);if(row==null)return denied();
        var scope=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(query.tenantId(),query.actorUserId(),row.getProjectId(),ProjectScopeApi.ACTION_VIEW,query.expectedScopeVersion()));
        return visible(scope,row.getProjectId())?fact(scope.treeVersion()):denied();
    }
    @Override public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query){
        return inspect(new FileBusinessObjectPolicyQuery(query.tenantId(),query.actorUserId(),query.key().ownerContext(),query.key().objectType(),query.key().objectId(),query.key().purposeCode(),query.key().purposeCode(),query.requiredAction()));
    }
    @Override public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery query){
        var row=locate(query.tenantId(),query.key().objectId(),true);
        if(row==null || !purpose(query.key().purposeCode()) || !Set.of(FileActionCodes.READ,FileActionCodes.DOWNLOAD,FileActionCodes.PREVIEW).contains(query.requiredAction())
                || !permissions.hasAnyPermissions(query.actorUserId(),"pms:imp-training:query"))return denied();
        var scope=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(query.tenantId(),query.actorUserId(),row.getProjectId(),ProjectScopeApi.ACTION_VIEW,query.expectedScopeVersion()));
        return visible(scope,row.getProjectId())?fact(scope.treeVersion()):denied();
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateNativeGeneratedFile(NativeGeneratedFilePolicyQuery query){
        if(!purpose(query.purposeCode()) || query.expectedOwnerVersion()==null
                || !query.purposeCode().endsWith("/"+query.expectedOwnerVersion()))throw deniedGeneration();
        var row=locate(query.tenantId(),String.valueOf(query.objectId()),true);
        if(row==null || !Objects.equals(row.getVersion(),query.expectedOwnerVersion()) || Integer.valueOf(3).equals(row.getStatus())
                || !permissions.hasAnyPermissions(query.actorUserId(),"pms:imp-training:update","pms:imp-training:issue"))throw deniedGeneration();
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(),query.actorUserId(),row.getProjectId(),ProjectScopeApi.ACTION_MANAGE));
        if(!visible(scope,row.getProjectId()) || query.expectedScopeVersion()!=null && !query.expectedScopeVersion().equals(scope.treeVersion()))throw deniedGeneration();
        scope=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(query.tenantId(),query.actorUserId(),row.getProjectId(),ProjectScopeApi.ACTION_MANAGE,scope.treeVersion()));
        if(!visible(scope,row.getProjectId()))throw deniedGeneration();
        var projectQuery=new ProjectAcceptanceContextApi.Query(query.tenantId(),row.getProjectId(),query.actorUserId());
        var observed=projects.inspect(projectQuery);
        if(observed==null)throw deniedGeneration();
        var locked=projects.lock(projectQuery,observed.projectVersion(),scope.treeVersion());
        if(locked==null || !Objects.equals(row.getProjectId(),locked.projectId()) || !"ACTIVE".equals(locked.lifecycleStatus())
                || !Objects.equals(scope.treeVersion(),locked.treeVersion()))throw deniedGeneration();
        return fact(scope.treeVersion());
    }
    private static boolean visible(ProjectScopeResult scope,Long project){return scope!=null && scope.treeVersion()!=null && scope.fullProjectIds()!=null && scope.fullProjectIds().contains(project)
            && (scope.placeholderProjectIds()==null || !scope.placeholderProjectIds().contains(project));}
    private static FileBusinessObjectPolicyFact fact(Long scope){
        // Same technical 50 MiB ceiling as the platform upload service, not a new business threshold.
        return new FileBusinessObjectPolicyFact(true,scope,"IMMUTABLE","MULTIPLE",Set.of("TRAINING_RECORD"),Set.of("text/html","application/pdf"),52_428_800L,"INTERNAL");
    }
    private static FileBusinessObjectPolicyFact denied(){return new FileBusinessObjectPolicyFact(false,null,null,null,Set.of(),Set.of(),null,null);}
    private static BusinessContractException deniedGeneration(){return new BusinessContractException("TRAINING_FILE_GENERATION_DENIED","Native training owner, version or project scope rejected generation");}
}
