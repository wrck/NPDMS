package cn.iocoder.yudao.module.pms.engineering.service.requirement;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialUploadPolicyValidator;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Component;import java.util.*;
@Component @RequiredArgsConstructor
public class RequirementAnalysisDeliveryAccess implements DeliveryMaterialUploadPolicyValidator {
 @Override public boolean allowsGenericDeliveryActions(String entityType){return false;}

 private final RequirementAnalysisMapper requirements;private final RequirementAnalysisAccess access;private final ProjectScopeApi scopes;
 public String ownerModule(){return "SOL";}
 public boolean supportsEntityType(String type){return "requirementAnalysis".equals(type);}
 public Long requireDeliveryAccess(Long tenant,Long actorId,String type,String id,String purpose,boolean write,boolean lock,Long expected){
  if(write||!supportsEntityType(type)||!Objects.equals(tenant,TenantContextHolder.getRequiredTenantId())||actorId==null||actorId<=0)throw denied();
  Long root;try{root=Long.valueOf(id);}catch(RuntimeException invalid){throw denied();}
  var row=requirements.selectLatestForEntity(new RequirementEntityQuery(tenant,root));if(row==null||!Objects.equals(row.getEntityId(),root)||!Objects.equals(row.getTenantId(),tenant))throw denied();
  var actor=new EntityActor(tenant,actorId,"DELIVERY_READ");access.read(row.getId(),actor);
  var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actorId,row.getProjectId(),ProjectScopeApi.ACTION_VIEW));if(!visible(scope,row.getProjectId())||expected!=null&&!Objects.equals(expected,scope.treeVersion()))throw denied();
  if(lock){var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actorId,row.getProjectId(),ProjectScopeApi.ACTION_VIEW,scope.treeVersion()));if(!visible(checked,row.getProjectId())||!Objects.equals(scope.treeVersion(),checked.treeVersion()))throw denied();var locked=requirements.lockRevision(new RequirementRevisionQuery(tenant,row.getId()));if(locked==null||!Objects.equals(locked.getEntityId(),root)||!Objects.equals(locked.getProjectId(),row.getProjectId()))throw denied();access.read(locked.getId(),actor);}
  return scope.treeVersion();
 }
 public FileBusinessObjectPolicyFact validateUpload(Long tenant,Long actor,String type,String id,String purpose,String action,boolean lock,Long scope){throw denied();}
 private static boolean visible(ProjectScopeResult s,Long id){return s!=null&&s.treeVersion()!=null&&s.fullProjectIds()!=null&&s.fullProjectIds().contains(id);}
 private static BusinessContractException denied(){return new BusinessContractException("RA_DELIVERY_ACCESS_DENIED","Native revision read authorization required; files are produced only by native field/save commands");}
}
