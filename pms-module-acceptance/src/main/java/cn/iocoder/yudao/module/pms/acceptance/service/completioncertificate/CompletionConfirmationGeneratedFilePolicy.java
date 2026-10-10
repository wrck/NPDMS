package cn.iocoder.yudao.module.pms.acceptance.service.completioncertificate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery;
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
public class CompletionConfirmationGeneratedFilePolicy implements FileBusinessObjectPolicyProvider,FileDocumentSourceProvider,BusinessGrantGeneratedFilePolicyProvider {
    private final cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionAccessGrantMapper grants;
    private final cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.CompletionSatisfactionBundleMapper bundles;
    private final cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.CompletionSatisfactionSigningMapper signings;
    private final CompletionCertificateMapper certificates;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    private final ProjectAcceptanceContextApi projects;
    @Override public String ownerContext(){return "ACC";}
    @Override public String objectType(){return "COMPLETION_CONFIRMATION";}
    @Override public List<Descriptor> descriptors(){return List.of(new Descriptor("COMPLETION_CERTIFICATE","完工及满意度确认"));}
    private static boolean purpose(String value){return value!=null && value.matches("(COMPLETION|SATISFACTION)_(PDF|HTML)/[0-9]+");}
    private cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate.CompletionCertificateDO locate(Long tenant,String id,boolean lock){
        if(!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()))return null;
        Long key;try{key=Long.valueOf(id);}catch(RuntimeException invalid){return null;}
        if(key<=0)return null;
        var row=lock?certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(tenant,key)):certificates.selectById(key);
        return row!=null && Objects.equals(tenant,row.getTenantId())?row:null;
    }
    @Override public Scope resolve(Long tenant,String owner,String type,String id,String purpose){
        if(!"ACC".equals(owner)||!"COMPLETION_CONFIRMATION".equals(type)||!purpose(purpose))return null;
        var row=locate(tenant,id,false);
        return row==null?null:new Scope(row.getProjectId(),"COMPLETION_CERTIFICATE","ACC","completionCertificate",row.getId(),null);
    }
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query){
        if(!Set.of(FileActionCodes.READ,FileActionCodes.DOWNLOAD,FileActionCodes.PREVIEW).contains(query.requiredAction())||!purpose(query.purposeCode()))return denied();
        var row=locate(query.tenantId(),query.objectId(),false);
        if(row==null||!permissions.hasAnyPermissions(query.actorUserId(),"pms:acc-completion-certificate:query"))return denied();
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
                || !permissions.hasAnyPermissions(query.actorUserId(),"pms:acc-completion-certificate:query"))return denied();
        var scope=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(query.tenantId(),query.actorUserId(),row.getProjectId(),ProjectScopeApi.ACTION_VIEW,query.expectedScopeVersion()));
        return visible(scope,row.getProjectId())?fact(scope.treeVersion()):denied();
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateNativeGeneratedFile(NativeGeneratedFilePolicyQuery query){
        if(!purpose(query.purposeCode()) || !query.purposeCode().contains("_PDF/") || query.expectedOwnerVersion()==null
                || !query.purposeCode().endsWith("/"+query.expectedOwnerVersion()))throw deniedGeneration();
        var row=locate(query.tenantId(),String.valueOf(query.objectId()),true);
        if(row==null || !Objects.equals(row.getVersion(),query.expectedOwnerVersion()) || Integer.valueOf(4).equals(row.getStatus())
                || !permissions.hasAnyPermissions(query.actorUserId(),"pms:acc-completion-certificate:update","pms:acc-completion-certificate:submit"))throw deniedGeneration();
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
        return new FileBusinessObjectPolicyFact(true,scope,"IMMUTABLE","MULTIPLE",Set.of("COMPLETION_CERTIFICATE"),Set.of("text/html","application/pdf"),52_428_800L,"INTERNAL");
    }
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public BusinessGrantGeneratedFilePolicyProvider.Policy lockAndRevalidate(BusinessGrantGeneratedFilePolicyQuery query){
        if(query==null || !"ACC".equals(query.ownerContext()) || !objectType().equals(query.objectType())
                || !purpose(query.purposeCode()) || !query.purposeCode().endsWith("_HTML/"+query.expectedOwnerVersion()))throw deniedGeneration();
        var row=locate(query.tenantId(),String.valueOf(query.objectId()),true);
        var grant=grants.selectByIdForUpdate(query.tenantId(),query.grantId());
        if(row==null || !(Integer.valueOf(2).equals(row.getStatus()) || Integer.valueOf(3).equals(row.getStatus())) || !Objects.equals(row.getVersion(),query.expectedOwnerVersion())
                || grant==null || !Objects.equals(query.tenantId(),grant.getTenantId()) || !"ACTIVE".equals(grant.getGrantStatus()) || grant.getGrantVersion()==null
                || !Objects.equals(grant.getGrantVersion().longValue(),query.issuanceVersion())
                || grant.getCompletionBundleId()==null || grant.getExpiresAt()==null || !java.time.LocalDateTime.now().isBefore(grant.getExpiresAt())
                || grant.getEffectiveFrom()==null || java.time.LocalDateTime.now().isBefore(grant.getEffectiveFrom()))throw deniedGeneration();
        var bundle=bundles.selectById(grant.getCompletionBundleId());
        if(bundle==null || !Objects.equals(bundle.getTenantId(),query.tenantId()) || !Objects.equals(bundle.getCertificateId(),row.getId())
                || !Objects.equals(bundle.getProjectId(),row.getProjectId()))throw deniedGeneration();
        var signing=signings.selectByQuestionnaire(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.CompletionSigningQuestionnaireQuery(
                query.tenantId(),bundle.getId(),grant.getQuestionnaireId()));
        if(signing==null || !Objects.equals(signing.getGrantId(),grant.getId()))throw deniedGeneration();
        Long actor;try{actor=Long.valueOf(grant.getCreator());}catch(RuntimeException invalid){throw deniedGeneration();}
        if(actor<=0 || !permissions.hasAnyPermissions(actor,"pms:acc-completion-certificate:audit"))throw deniedGeneration();
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(query.tenantId(),actor,row.getProjectId(),ProjectScopeApi.ACTION_MANAGE));
        if(!visible(scope,row.getProjectId()) || query.expectedScopeVersion()!=null && !Objects.equals(scope.treeVersion(),query.expectedScopeVersion()))throw deniedGeneration();
        var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(query.tenantId(),actor,row.getProjectId(),ProjectScopeApi.ACTION_MANAGE,scope.treeVersion()));
        if(!visible(checked,row.getProjectId()) || !Objects.equals(scope.treeVersion(),checked.treeVersion()))throw deniedGeneration();
        var projectQuery=new ProjectAcceptanceContextApi.Query(query.tenantId(),row.getProjectId(),actor);
        var observed=projects.inspect(projectQuery);
        if(observed==null)throw deniedGeneration();
        var locked=projects.lock(projectQuery,observed.projectVersion(),checked.treeVersion());
        if(locked==null || !Objects.equals(row.getProjectId(),locked.projectId()) || !"ACTIVE".equals(locked.lifecycleStatus())
                || !Objects.equals(checked.treeVersion(),locked.treeVersion()))throw deniedGeneration();
        return new BusinessGrantGeneratedFilePolicyProvider.Policy(grant.getId(),grant.getGrantVersion().longValue(),actor,fact(checked.treeVersion()));
    }
    private static FileBusinessObjectPolicyFact denied(){return new FileBusinessObjectPolicyFact(false,null,null,null,Set.of(),Set.of(),null,null);}
    private static BusinessContractException deniedGeneration(){return new BusinessContractException("COMPLETION_DOCUMENT_GENERATION_DENIED","Completion confirmation owner, version or project scope rejected generation");}
}
