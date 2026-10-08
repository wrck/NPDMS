package cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.BriefingMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.briefing.query.BriefingFileOwnerQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
@Component @RequiredArgsConstructor
public class SupplementalAttachmentAccess {
 private final BriefingMapper briefings;
 private final PermissionApi permissions;private final ProjectScopeApi scopes;private final ProjectAcceptanceContextApi projects;
 private static final Set<String> WRITES=Set.of(FileActionCodes.UPLOAD,FileActionCodes.REPLACE,FileActionCodes.REFERENCE,FileActionCodes.DETACH);
 private static final Set<String> READS=Set.of(FileActionCodes.READ,FileActionCodes.DOWNLOAD,FileActionCodes.PREVIEW);
 public record Owner(Long id,Long tenant,Long project,Integer status,Long version){}
 public Owner owner(SupplementalAttachmentKind kind,Long tenant,Long id,boolean lock){
  var row=lock?briefings.selectFileOwnerForUpdate(new BriefingFileOwnerQuery(tenant,id)):briefings.selectById(id);return row==null?null:new Owner(row.getId(),row.getTenantId(),row.getProjectId(),row.getStatus(),row.getVersion());
 }
 public FileBusinessObjectPolicyFact require(SupplementalAttachmentKind kind,Long tenant,Long actor,String id,String purpose,String action,boolean lock,Long expected,boolean freeze){
  var principal=SecurityFrameworkUtils.getLoginUser();
  if(kind!=SupplementalAttachmentKind.BRIEFING||!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId())||actor==null||actor<=0||principal==null||!Objects.equals(actor,principal.getId())||!Objects.equals(tenant,principal.getTenantId())||!kind.getPurpose().equals(purpose)||!(WRITES.contains(action)||READS.contains(action)))throw denied();
  Long key;try{key=Long.valueOf(id);}catch(RuntimeException invalid){throw denied();}if(key<=0)throw denied();
  var row=owner(kind,tenant,key,lock);boolean write=WRITES.contains(action);
  if(row==null||!Objects.equals(tenant,row.tenant())||row.version()==null||row.version()<0||!permissions.hasAnyPermissions(actor,kind.getPermission()+":query"))throw denied();
  if(write&&(!Integer.valueOf(0).equals(row.status())||!permissions.hasAnyPermissions(actor,kind.getPermission()+(freeze?kind.getFreezePermission():":update"))))throw denied();
  Long version;
  if(row.project()==null)throw denied();String scopeAction=write?ProjectScopeApi.ACTION_MANAGE:ProjectScopeApi.ACTION_VIEW;
   var seen=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,row.project(),scopeAction));if(!visible(seen,row.project())||expected!=null&&!expected.equals(seen.treeVersion()))throw denied();
   if(lock){var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,row.project(),scopeAction,seen.treeVersion()));if(!visible(checked,row.project())||!Objects.equals(seen.treeVersion(),checked.treeVersion()))throw denied();}
   version=seen.treeVersion();if(write){var q=new ProjectAcceptanceContextApi.Query(tenant,row.project(),actor);var seenProject=projects.inspect(q);if(seenProject==null)throw denied();var checked=lock?projects.lock(q,seenProject.projectVersion(),version):seenProject;if(checked==null||!Objects.equals(row.project(),checked.projectId())||!Objects.equals(version,checked.treeVersion())||!"ACTIVE".equals(checked.lifecycleStatus()))throw denied();}
  return new FileBusinessObjectPolicyFact(true,version,Integer.valueOf(0).equals(row.status())?"MUTABLE":"IMMUTABLE","MULTIPLE",Set.of(kind.getPurpose()),Set.of("application/msword","application/vnd.ms-excel","application/vnd.ms-powerpoint","text/plain","application/pdf"),5_242_880L,"INTERNAL");
 }
 private static boolean visible(ProjectScopeResult s,Long id){return s!=null&&s.treeVersion()!=null&&s.treeVersion()>=0&&s.fullProjectIds()!=null&&s.fullProjectIds().contains(id)&&(s.placeholderProjectIds()==null||!s.placeholderProjectIds().contains(id));}
 private static BusinessContractException denied(){return new BusinessContractException("SUPPLEMENTAL_ATTACHMENT_ACCESS_DENIED","Actual native Owner tenant, permission, version, purpose and state required");}
}
