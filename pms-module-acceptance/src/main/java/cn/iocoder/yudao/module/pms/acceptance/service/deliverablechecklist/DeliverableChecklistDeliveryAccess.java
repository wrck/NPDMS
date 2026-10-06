package cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.*;
@Service @RequiredArgsConstructor
public class DeliverableChecklistDeliveryAccess {
 private final PermissionApi permissions;private final ProjectScopeApi scopes;
 public void require(DeliverableChecklistDO row,String action){
  Long tenant=TenantContextHolder.getRequiredTenantId(),actor=SecurityFrameworkUtils.getLoginUserId();
  if(row==null||!Objects.equals(row.getTenantId(),tenant)||actor==null||row.getProjectId()==null||!Set.of("submit","audit","update","delete").contains(action)||!permissions.hasAnyPermissions(actor,"pms:acc-deliverable-checklist:"+action))throw denied();
  var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,row.getProjectId(),ProjectScopeApi.ACTION_MANAGE));if(!visible(scope,row.getProjectId()))throw denied();
  var locked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,row.getProjectId(),ProjectScopeApi.ACTION_MANAGE,scope.treeVersion()));if(!visible(locked,row.getProjectId())||!Objects.equals(scope.treeVersion(),locked.treeVersion()))throw denied();
 }
 private static boolean visible(ProjectScopeResult s,Long id){return s!=null&&s.treeVersion()!=null&&s.fullProjectIds()!=null&&s.fullProjectIds().contains(id);}
 private static BusinessContractException denied(){return new BusinessContractException("CHECKLIST_DELIVERY_ACCESS_DENIED","Native checklist permission and project scope required");}
}
