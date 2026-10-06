package cn.iocoder.yudao.module.pms.engineering.service.arrival;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import java.util.*;
@Service @RequiredArgsConstructor
public class ArrivalDeliveryRegistration {
 private final PermissionApi permissions;private final ProjectScopeApi scopes;private final FileArtifactApi files;private final PlatformDeliveryMaterialApi materials;
 public void requireWrite(ArrivalDO row){
  Long tenant=TenantContextHolder.getRequiredTenantId(),actor=SecurityFrameworkUtils.getLoginUserId();
  if(row==null||!Objects.equals(tenant,row.getTenantId())||actor==null||row.getProjectId()==null||!permissions.hasAnyPermissions(actor,"pms:imp-arrival:update"))throw denied();
  var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,row.getProjectId(),ProjectScopeApi.ACTION_MANAGE));
  if(!visible(scope,row.getProjectId()))throw denied();var locked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,row.getProjectId(),ProjectScopeApi.ACTION_MANAGE,scope.treeVersion()));
  if(!visible(locked,row.getProjectId())||!Objects.equals(scope.treeVersion(),locked.treeVersion()))throw denied();
 }
 public void registerFiles(ArrivalDO row){
  var key=new FileReferenceSetKey("IMP","ARRIVAL",row.getId().toString(),ArrivalFilePolicyProvider.PURPOSE_CODE);
  var sets=files.inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(key),FileActionCodes.READ));
  var locked=files.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(sets.stream().map(s->new FileReferenceSetExpectation(s.key(),s.scopeVersion(),s.activeFacts())).toList(),FileActionCodes.READ));
  locked.forEach(set->set.activeFacts().forEach(materials::registerNativeSourceFile));
 }
 public void preventSourceMove(ArrivalDO row,Long project){if(!Objects.equals(row.getProjectId(),project)&&!materials.listByEntity("IMP","arrival",row.getId()).isEmpty())throw denied();}
 public void registerSigned(ArrivalDO row){materials.registerBusinessResultMaterial("IMP","arrival",row.getId(),"RECEIPT","arrival",row.getId().toString(),null,row.getCode(),row.getProjectId());}
 private static boolean visible(ProjectScopeResult s,Long id){return s!=null&&s.treeVersion()!=null&&s.fullProjectIds()!=null&&s.fullProjectIds().contains(id);}
 private static BusinessContractException denied(){return new BusinessContractException("ARRIVAL_DELIVERY_ACCESS_DENIED","Actual arrival permission, project scope and source ownership required");}
}
