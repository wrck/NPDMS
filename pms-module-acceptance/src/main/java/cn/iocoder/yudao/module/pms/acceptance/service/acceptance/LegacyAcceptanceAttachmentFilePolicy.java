package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.AcceptanceOwnerLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** Native attachments keep the existing draft-only update boundary and legacy upload media/size. */
@Component @RequiredArgsConstructor
public class LegacyAcceptanceAttachmentFilePolicy implements FileBusinessObjectPolicyProvider {
    public static final String TYPE = "acceptance";
    public static final String PURPOSE = "LEGACY_ACCEPTANCE_ATTACHMENT";
    private static final Set<String> WRITES = Set.of(FileActionCodes.UPLOAD,FileActionCodes.REPLACE,FileActionCodes.REFERENCE,FileActionCodes.DETACH);
    private static final Set<String> READS = Set.of(FileActionCodes.READ,FileActionCodes.DOWNLOAD,FileActionCodes.PREVIEW);
    private static final Set<String> MEDIA = Set.of("application/msword","application/vnd.ms-excel","application/vnd.ms-powerpoint","text/plain","application/pdf");
    private final AcceptanceMapper rows;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    private final ProjectAcceptanceContextApi projects;
    @Override public String ownerContext() { return "ACC"; }
    @Override public String objectType() { return TYPE; }
    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return require(query.tenantId(),query.actorUserId(),query.objectId(),query.purposeCode(),query.requiredAction(),false,null);
    }
    @Override public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return require(query.tenantId(),query.actorUserId(),query.objectId(),query.purposeCode(),query.requiredAction(),true,query.expectedScopeVersion());
    }
    @Override public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        return require(query.tenantId(),query.actorUserId(),query.key().objectId(),query.key().purposeCode(),query.requiredAction(),false,null);
    }
    @Override public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery query) {
        return require(query.tenantId(),query.actorUserId(),query.key().objectId(),query.key().purposeCode(),query.requiredAction(),true,query.expectedScopeVersion());
    }
    public FileBusinessObjectPolicyFact require(Long tenant,Long actor,String object,String purpose,String action,boolean lock,Long expected) {
        return require(tenant,actor,object,purpose,action,lock,expected,false);
    }
    public FileBusinessObjectPolicyFact require(Long tenant,Long actor,String object,String purpose,String action,boolean lock,Long expected,boolean submit) {
        var principal=SecurityFrameworkUtils.getLoginUser();
        if(!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()) || actor==null || actor<=0 || principal==null
                || !Objects.equals(actor,principal.getId()) || !Objects.equals(tenant,principal.getTenantId())
                || !PURPOSE.equals(purpose) || !(WRITES.contains(action)||READS.contains(action)))throw denied();
        Long id;try{id=Long.valueOf(object);}catch(RuntimeException invalid){throw denied();}if(id<=0)throw denied();
        var row=lock?rows.selectOwnerForUpdate(new AcceptanceOwnerLockQuery(tenant,id)):rows.selectById(id);
        boolean write=WRITES.contains(action);
        if(row==null || !Objects.equals(tenant,row.getTenantId()) || row.getProjectId()==null || row.getVersion()==null || row.getVersion()<0
                || !permissions.hasAnyPermissions(actor,"pms:acc-acceptance:query"))throw denied();
        if(write && (!Integer.valueOf(0).equals(row.getStatus()) || !permissions.hasAnyPermissions(actor,submit?"pms:acc-acceptance:submit":"pms:acc-acceptance:update")))throw denied();
        String scopeAction=write?ProjectScopeApi.ACTION_MANAGE:ProjectScopeApi.ACTION_VIEW;
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,row.getProjectId(),scopeAction));
        if(!visible(scope,row.getProjectId()) || expected!=null && !Objects.equals(expected,scope.treeVersion()))throw denied();
        if(lock){var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,row.getProjectId(),scopeAction,scope.treeVersion()));
            if(!visible(checked,row.getProjectId()) || !Objects.equals(scope.treeVersion(),checked.treeVersion()))throw denied();}
        if(write){var query=new ProjectAcceptanceContextApi.Query(tenant,row.getProjectId(),actor);var project=projects.inspect(query);if(project==null)throw denied();
            var checked=lock?projects.lock(query,project.projectVersion(),scope.treeVersion()):project;
            if(checked==null || !Objects.equals(row.getProjectId(),checked.projectId()) || !Objects.equals(scope.treeVersion(),checked.treeVersion()) || !"ACTIVE".equals(checked.lifecycleStatus()))throw denied();}
        return new FileBusinessObjectPolicyFact(true,scope.treeVersion(),Integer.valueOf(0).equals(row.getStatus())?"MUTABLE":"IMMUTABLE","MULTIPLE",Set.of(PURPOSE),MEDIA,5_242_880L,"INTERNAL");
    }
    private static boolean visible(ProjectScopeResult s,Long id){return s!=null&&s.treeVersion()!=null&&s.treeVersion()>=0&&s.fullProjectIds()!=null&&s.fullProjectIds().contains(id)&&(s.placeholderProjectIds()==null||!s.placeholderProjectIds().contains(id));}
    private static BusinessContractException denied(){return new BusinessContractException("LEGACY_ACCEPTANCE_ATTACHMENT_ACCESS_DENIED","Actual native legacy acceptance tenant, permission, draft state and managed project required");}
}
