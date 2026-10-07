package cn.iocoder.yudao.module.pms.engineering.service.training;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.*;
import cn.iocoder.yudao.module.pms.platform.api.file.*;import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.*;
import java.util.*;import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class TrainingConfirmationGrantService implements BusinessGrantGeneratedFilePolicyProvider {
 private final TrainingMapper training;private final TrainingConfirmationGrantMapper grants;private final PermissionApi permissions;
 private final ProjectScopeApi scopes;private final ProjectAcceptanceContextApi projects;
 @Override public String ownerContext(){return "IMP";}@Override public String objectType(){return "TRAINING_RECORD";}
 @Transactional(propagation=Propagation.MANDATORY)
 public TrainingConfirmationGrantDO createForIssue(Long id,String digest){
  Long tenant=TenantContextHolder.getRequiredTenantId(),actor=SecurityFrameworkUtils.getLoginUserId();
  var row=training.selectFileOwnerForUpdate(new TrainingFileOwnerQuery(tenant,id));
  LocalDateTime expires=row==null?null:row.getTokenExpiresAt();
  if(actor==null||actor<=0||row==null||!Objects.equals(tenant,row.getTenantId())||!Integer.valueOf(1).equals(row.getStatus())
   ||digest==null||!digest.matches("[0-9a-f]{64}")||!digest.equals(row.getSignTokenDigest())
   ||expires==null||!LocalDateTime.now().isBefore(expires)||row.getConfirmationFormRules()==null)throw denied();
  Long scope=requireScope(tenant,actor,row,null);Long last=grants.selectLatestIssuance(new TrainingGrantIssuanceQuery(tenant,id));
  var grant=new TrainingConfirmationGrantDO();grant.setTenantId(tenant);grant.setTrainingId(id);grant.setIssuanceVersion(last==null?1L:Math.addExact(last,1));
  grant.setTokenDigest(digest);grant.setIssuedByUserId(actor);grant.setIssuedAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));grant.setExpiresAt(expires);grant.setScopeVersion(scope);
  grant.setConfirmationRevisionId(row.getConfirmationRevisionId());grant.setConfirmationRulesSha256(org.apache.commons.codec.digest.DigestUtils.sha256Hex(row.getConfirmationFormRules()));
  if(grants.insert(grant)!=1)throw denied();return grant;
 }
 public TrainingConfirmationGrantDO findForConfirmation(TrainingDO row){
  if(row==null||!Objects.equals(TenantContextHolder.getRequiredTenantId(),row.getTenantId()))throw denied();
  var current=grants.selectCurrentGrant(new TrainingConfirmationGrantQuery(row.getTenantId(),row.getId(),row.getSignTokenDigest()));
  // Once this root has issuance evidence, loss of its current grant must fail closed.
  // Only roots with no issuance history retain the separately preserved legacy path.
  if(current==null && grants.selectLatestIssuance(new TrainingGrantIssuanceQuery(row.getTenantId(),row.getId()))!=null)throw denied();
  return current;
 }
 @Override @Transactional(propagation=Propagation.MANDATORY)
 public Policy lockAndRevalidate(BusinessGrantGeneratedFilePolicyQuery q){
  if(q==null||!"IMP".equals(q.ownerContext())||!"TRAINING_RECORD".equals(q.objectType())||!Objects.equals(q.tenantId(),TenantContextHolder.getRequiredTenantId())
   ||q.objectId()==null||q.expectedOwnerVersion()==null||!Objects.equals(q.purposeCode(),"TRAINING_RECORD_HTML/"+q.expectedOwnerVersion()))throw denied();
  var row=training.selectFileOwnerForUpdate(new TrainingFileOwnerQuery(q.tenantId(),q.objectId()));
  if(row==null||!Objects.equals(q.tenantId(),row.getTenantId())||!Integer.valueOf(1).equals(row.getStatus())||!Objects.equals(row.getVersion(),q.expectedOwnerVersion()))throw denied();
  var grant=grants.selectCurrentGrant(new TrainingConfirmationGrantQuery(q.tenantId(),row.getId(),row.getSignTokenDigest()));
  if(grant==null||!Objects.equals(q.grantId(),grant.getId())||!Objects.equals(q.issuanceVersion(),grant.getIssuanceVersion())
   ||grant.getScopeVersion()==null||grant.getScopeVersion()<0
   ||!Objects.equals(q.tenantId(),grant.getTenantId())||!Objects.equals(row.getId(),grant.getTrainingId())||grant.getExpiresAt()==null
   ||!LocalDateTime.now().isBefore(grant.getExpiresAt())||grant.getIssuedAt()==null||LocalDateTime.now().isBefore(grant.getIssuedAt())
   ||!Objects.equals(row.getTokenExpiresAt(),grant.getExpiresAt())||!Objects.equals(row.getConfirmationRevisionId(),grant.getConfirmationRevisionId())
   ||row.getConfirmationFormRules()==null||!Objects.equals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(row.getConfirmationFormRules()),grant.getConfirmationRulesSha256())
   ||q.expectedScopeVersion()!=null&&!Objects.equals(q.expectedScopeVersion(),grant.getScopeVersion()))throw denied();
  Long scope=requireScope(q.tenantId(),grant.getIssuedByUserId(),row,grant.getScopeVersion());
  return new Policy(grant.getId(),grant.getIssuanceVersion(),grant.getIssuedByUserId(),new FileBusinessObjectPolicyFact(true,scope,"IMMUTABLE","MULTIPLE",
   Set.of("TRAINING_RECORD"),Set.of("text/html"),52_428_800L,"INTERNAL"));
 }
 private Long requireScope(Long tenant,Long actor,TrainingDO row,Long expected){
  if(actor==null||actor<=0||row.getProjectId()==null||!permissions.hasAnyPermissions(actor,"pms:imp-training:issue"))throw denied();
  var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant,actor,row.getProjectId(),ProjectScopeApi.ACTION_MANAGE));
  if(!visible(scope,row.getProjectId())||expected!=null&&!Objects.equals(expected,scope.treeVersion()))throw denied();
  var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant,actor,row.getProjectId(),ProjectScopeApi.ACTION_MANAGE,scope.treeVersion()));
  if(!visible(checked,row.getProjectId())||!Objects.equals(scope.treeVersion(),checked.treeVersion()))throw denied();
  var query=new ProjectAcceptanceContextApi.Query(tenant,row.getProjectId(),actor);var observed=projects.inspect(query);
  if(observed==null)throw denied();var locked=projects.lock(query,observed.projectVersion(),scope.treeVersion());
  if(locked==null||!Objects.equals(row.getProjectId(),locked.projectId())||!Objects.equals(scope.treeVersion(),locked.treeVersion())||!"ACTIVE".equals(locked.lifecycleStatus()))throw denied();
  return scope.treeVersion();
 }
 private static boolean visible(ProjectScopeResult s,Long p){return s!=null&&s.treeVersion()!=null&&s.fullProjectIds()!=null&&s.fullProjectIds().contains(p)
  &&(s.placeholderProjectIds()==null||!s.placeholderProjectIds().contains(p));}
 private static BusinessContractException denied(){return new BusinessContractException("TRAINING_CONFIRMATION_GRANT_INVALID","Actual current training grant, frozen form, issuer scope and native version required");}
}
