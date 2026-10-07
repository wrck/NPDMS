package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.ArchiveDocumentMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query.ArchiveDocumentDeliveryLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.CompletionCertificateMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query.CompletionCertificateDeliveryLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
@Component @RequiredArgsConstructor
public class NativeAcceptanceDeliveryAccess {
    private final ArchiveDocumentMapper archives;
    private final CompletionCertificateMapper certificates;
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    private final ProjectAcceptanceContextApi projects;
    public boolean supports(String type) { return "archiveDocument".equals(type) || "completionCertificate".equals(type); }
    public Long require(Long tenant,Long actor,String type,String objectId,String purpose,boolean write,boolean lock,Long expectedScope) {
        if(!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()) || actor==null || actor<=0 || !supports(type)) throw denied();
        Long id; try { id=Long.valueOf(objectId); } catch(RuntimeException invalid) { throw denied(); }
        if(id<=0) throw denied();
        Long projectId; Integer status; String permission;
        if("archiveDocument".equals(type)) {
            var row=lock?archives.selectDeliveryOwnerForUpdate(new ArchiveDocumentDeliveryLockQuery(tenant,id)):archives.selectById(id);
            if(row==null || !Objects.equals(tenant,row.getTenantId()))throw denied();
            projectId=row.getProjectId();status=row.getStatus();permission="pms:acc-archive-document:";
            if(purpose!=null && !purpose.isBlank() && !"ARCHIVE_DOCUMENT".equals(purpose))throw denied();
            if(write && !Integer.valueOf(0).equals(status))throw denied();
        } else {
            var row=lock?certificates.selectDeliveryOwnerForUpdate(new CompletionCertificateDeliveryLockQuery(tenant,id)):certificates.selectById(id);
            if(row==null || !Objects.equals(tenant,row.getTenantId()))throw denied();
            projectId=row.getProjectId();status=row.getStatus();permission="pms:acc-completion-certificate:";
            if(purpose!=null && !purpose.isBlank() && !"COMPLETION_CERTIFICATE".equals(purpose))throw denied();
            // This result is created by native customer-confirm/archive commands, never by panel writes.
            if(write)throw denied();
        }
        if(projectId==null || !permissions.hasAnyPermissions(actor,permission+"query")
                || write && !permissions.hasAnyPermissions(actor,permission+"update"))throw denied();
        String action=write?ProjectScopeApi.ACTION_MANAGE:ProjectScopeApi.ACTION_VIEW;
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,projectId,action));
        if(scope==null || scope.treeVersion()==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(projectId)
                || expectedScope!=null && !Objects.equals(expectedScope,scope.treeVersion()))throw denied();
        if(lock) {
            var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,projectId,action,scope.treeVersion()));
            if(checked==null || !Objects.equals(scope.treeVersion(),checked.treeVersion()) || checked.fullProjectIds()==null
                    || !checked.fullProjectIds().contains(projectId))throw denied();
        }
        if(write) {
            var query=new ProjectAcceptanceContextApi.Query(tenant,projectId,actor);
            var project=projects.inspect(query);
            if(project==null)throw denied();
            var checked=lock?projects.lock(query,project.projectVersion(),scope.treeVersion()):project;
            if(checked==null || !Objects.equals(projectId,checked.projectId()) || !Objects.equals(scope.treeVersion(),checked.treeVersion())
                    || !"ACTIVE".equals(checked.lifecycleStatus()))throw denied();
        }
        return scope.treeVersion();
    }
    /** Native lifecycle commands retain their existing state machine and function permission. */
    public Long requireCommand(Long tenant,Long actor,String type,Long projectId,String action) {
        if(!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId()) || actor==null || actor<=0
                || projectId==null || !supports(type))throw denied();
        String prefix="archiveDocument".equals(type)?"pms:acc-archive-document:":"pms:acc-completion-certificate:";
        if(!java.util.Set.of("submit","audit").contains(action) || !permissions.hasAnyPermissions(actor,prefix+action))throw denied();
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,projectId,ProjectScopeApi.ACTION_MANAGE));
        if(scope==null || scope.treeVersion()==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(projectId))throw denied();
        var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,projectId,ProjectScopeApi.ACTION_MANAGE,scope.treeVersion()));
        if(checked==null || !Objects.equals(scope.treeVersion(),checked.treeVersion()) || checked.fullProjectIds()==null
                || !checked.fullProjectIds().contains(projectId))throw denied();
        return checked.treeVersion();
    }
    private static BusinessContractException denied(){return new BusinessContractException("DELIVERY_ACCESS_DENIED","Native delivery permission, project scope or business state does not permit this action");}
}
