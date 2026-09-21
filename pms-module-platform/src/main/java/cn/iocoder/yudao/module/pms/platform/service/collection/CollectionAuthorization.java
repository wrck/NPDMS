package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
@Component @RequiredArgsConstructor
public class CollectionAuthorization {
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    public void permission(Long actor,String permission) {
        if (actor==null || !permissions.hasAnyPermissions(actor,permission)) throw new AccessDeniedException("无设备采集操作权限");
    }
    public boolean allowed(Long actor,String permission) { return actor!=null && permissions.hasAnyPermissions(actor,permission); }
    public void project(Long actor,Long project,boolean edit,boolean lock) {
        if (project==null) throw new CollectionOperationException("请选择项目");
        String action=edit?ProjectScopeApi.ACTION_EDIT:ProjectScopeApi.ACTION_VIEW;
        var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant(),actor,project,action));
        if(scope==null || !scope.fullProjectIds().contains(project)) throw new AccessDeniedException("无当前项目权限");
        if(lock) {
            var current=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant(),actor,project,action,scope.treeVersion()));
            if(current==null || !java.util.Objects.equals(current.treeVersion(),scope.treeVersion()) || !current.fullProjectIds().contains(project)) throw new AccessDeniedException("项目权限已变化");
        }
    }
    public static Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
}
