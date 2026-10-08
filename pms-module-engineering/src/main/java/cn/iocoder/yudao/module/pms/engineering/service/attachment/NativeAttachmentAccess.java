package cn.iocoder.yudao.module.pms.engineering.service.attachment;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
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

@Component @RequiredArgsConstructor
public class NativeAttachmentAccess {
    private static final Set<String> WRITES=Set.of(FileActionCodes.UPLOAD,FileActionCodes.REPLACE,FileActionCodes.REFERENCE,FileActionCodes.DETACH);
    private static final Set<String> READS=Set.of(FileActionCodes.READ,FileActionCodes.DOWNLOAD,FileActionCodes.PREVIEW);
    private static final Set<String> MEDIA=Set.of("application/msword","application/vnd.ms-excel","application/vnd.ms-powerpoint","text/plain","application/pdf");
    private final NativeAttachmentOwners owners;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    private final ProjectAcceptanceContextApi projects;
    public FileBusinessObjectPolicyFact require(NativeAttachmentKind kind,Long tenant,Long actor,String object,String purpose,String action,boolean lock,Long expected) {
        var principal=SecurityFrameworkUtils.getLoginUser();
        if(kind==null || !Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()) || actor==null || actor<=0 || principal==null
                || !Objects.equals(actor,principal.getId()) || !Objects.equals(tenant,principal.getTenantId())
                || !kind.getPurpose().equals(purpose) || !(WRITES.contains(action)||READS.contains(action)))throw denied();
        Long id;try{id=Long.valueOf(object);}catch(RuntimeException invalid){throw denied();}if(id<=0)throw denied();
        var row=owners.find(kind,tenant,id,lock);boolean write=WRITES.contains(action);
        if(row==null || !Objects.equals(tenant,row.tenantId()) || row.projectId()==null
                || !permissions.hasAnyPermissions(actor,kind.getPermission()+":query"))throw denied();
        if(write && (!kind.mutable(row.status()) || !permissions.hasAnyPermissions(actor,kind.getPermission()+":update")))throw denied();
        String scopeAction=write?ProjectScopeApi.ACTION_MANAGE:ProjectScopeApi.ACTION_VIEW;
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,row.projectId(),scopeAction));
        if(!visible(scope,row.projectId()) || expected!=null&&!Objects.equals(expected,scope.treeVersion()))throw denied();
        if(lock){var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,row.projectId(),scopeAction,scope.treeVersion()));
            if(!visible(checked,row.projectId()) || !Objects.equals(scope.treeVersion(),checked.treeVersion()))throw denied();}
        if(write){var query=new ProjectAcceptanceContextApi.Query(tenant,row.projectId(),actor);var project=projects.inspect(query);if(project==null)throw denied();
            var checked=lock?projects.lock(query,project.projectVersion(),scope.treeVersion()):project;
            if(checked==null || !Objects.equals(row.projectId(),checked.projectId()) || !Objects.equals(scope.treeVersion(),checked.treeVersion()) || !"ACTIVE".equals(checked.lifecycleStatus()))throw denied();}
        return new FileBusinessObjectPolicyFact(true,scope.treeVersion(),kind.mutable(row.status())?"MUTABLE":"IMMUTABLE","MULTIPLE",Set.of(kind.getPurpose()),MEDIA,5_242_880L,"INTERNAL");
    }
    private static boolean visible(ProjectScopeResult s,Long id){return s!=null&&s.treeVersion()!=null&&s.treeVersion()>=0&&s.fullProjectIds()!=null&&s.fullProjectIds().contains(id)&&(s.placeholderProjectIds()==null||!s.placeholderProjectIds().contains(id));}
    private static BusinessContractException denied(){return new BusinessContractException("NATIVE_ATTACHMENT_ACCESS_DENIED","Actual native Owner tenant, permission, state and project scope required");}
}
