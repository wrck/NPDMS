package cn.iocoder.yudao.module.pms.engineering.service.solution;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.query.SolutionResultLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.util.*;import java.nio.charset.StandardCharsets;import org.springframework.web.util.UriUtils;
/** Customer-provided implementation document replaces the displayed solution body; it is not a loose input attachment. */
@Service @RequiredArgsConstructor
public class SolutionCustomerDocumentService {
 public static final String CODE="IMPLEMENTATION_PLAN",PREFIX="/api/v1/pms/solutions/";
 private final SolutionMapper solutions;private final PermissionApi permissions;private final ProjectScopeApi scopes;private final ProjectAcceptanceContextApi projects;
 private final FileEvidenceApi evidence;private final FileArtifactApi files;private final PlatformDeliveryMaterialApi materials;private final NativeGeneratedFileApi downloads;
 public record Attach(Integer expectedVersion,List<Long> referenceIds){}
 public record Attached(Long version,String customerPlanUrl,List<Long> materialIds){}
 @Transactional(rollbackFor=Exception.class)
 public Attached attach(Long id,Attach request){
  var row=authorize(id,true);if(request==null||request.expectedVersion()==null||!Integer.valueOf(0).equals(row.getStatus()))throw denied("SOLUTION_CUSTOMER_DOCUMENT_VERSION");
  if(request.referenceIds()==null||request.referenceIds().isEmpty()||request.referenceIds().size()>5||new HashSet<>(request.referenceIds()).size()!=request.referenceIds().size())throw denied("SOLUTION_CUSTOMER_DOCUMENT_FILES");
  List<Long> ids=new ArrayList<>();List<String> urls=new ArrayList<>();
  for(Long reference:request.referenceIds()){
   var document=evidence.inspectDocument(tenant(),reference);
   if(document==null||!document.available()||!"PLT".equals(document.ownerContext())||!"DELIVERY_MATERIAL".equals(document.objectType())||!("SOL:solution:"+id).equals(document.objectId())||!CODE.equals(document.purposeCode()))throw denied("SOLUTION_CUSTOMER_DOCUMENT_OWNER");
   var fact=files.inspect(new FileArtifactVersionQuery(document.artifactId(),document.versionNo(),document.ownerContext(),document.objectType(),document.objectId(),document.purposeCode(),document.referenceKey(),FileActionCodes.READ));
   files.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(document.artifactId(),document.versionNo(),document.ownerContext(),document.objectType(),document.objectId(),document.purposeCode(),document.referenceKey(),FileActionCodes.READ,fact.fileFactVersion(),fact.scopeVersion()));
   Long material=materials.registerNativeUploadedFile(fact);ids.add(material);
   urls.add(PREFIX+id+"/customer-files/"+material+"/"+UriUtils.encodePathSegment(document.name(),StandardCharsets.UTF_8).replace(",","%2C"));
  }
  String url=String.join(",",urls);Map<String,Object> remark=envelope(row.getRemark());
  if(!url.equals(remark.get("customerPlanUrl"))){if(!Objects.equals(row.getVersion(),request.expectedVersion().longValue()))throw denied("SOLUTION_CUSTOMER_DOCUMENT_VERSION");remark.put("hasCustomerPlan","yes");remark.put("customerPlanUrl",url);row.setRemark(JsonUtils.toJsonString(remark));long previousVersion=row.getVersion();if(solutions.updateById(row)!=1)throw denied("SOLUTION_CUSTOMER_DOCUMENT_VERSION");row.setVersion(previousVersion+1);}
  return new Attached(row.getVersion(),url,List.copyOf(ids));
 }
 public String download(Long id,Long material){authorize(id,false);requireMaterial(id,material);return downloads.requestDownload("SOL","solution",id,material);}
 public void validatePointer(Long id,String remark){Object value=envelope(remark).get("customerPlanUrl");if(!(value instanceof String url))return;
  for(String part:url.split(",")){if(!part.startsWith(PREFIX))continue;var match=java.util.regex.Pattern.compile("^/api/v1/pms/solutions/([0-9]+)/customer-files/([0-9]+)(/[^?#]+)?$").matcher(part);if(!match.matches()||id==null||!String.valueOf(id).equals(match.group(1)))throw denied("SOLUTION_CUSTOMER_DOCUMENT_OWNER");requireMaterial(id,Long.valueOf(match.group(2)));}
 }
 public static boolean hasNativePointer(String remark){return remark!=null&&remark.contains(PREFIX);}
 private void requireMaterial(Long id,Long material){boolean valid=materials.listByEntityAndType("SOL","solution",id,CODE).stream().anyMatch(m->material.equals(m.id())&&"ACTIVE".equals(m.status())&&"FILE".equals(m.materialKind()));if(!valid)throw denied("SOLUTION_CUSTOMER_DOCUMENT_UNAVAILABLE");}
 private SolutionDO authorize(Long id,boolean write){Long actor=SecurityFrameworkUtils.getLoginUserId();if(actor==null||actor<=0||!permissions.hasAnyPermissions(actor,write?"pms:sol-solution:update":"pms:sol-solution:query")||write&&(!permissions.hasAnyPermissions(actor,"pms:file:upload")||!permissions.hasAnyPermissions(actor,"pms:delivery:operate")))throw denied("SOLUTION_CUSTOMER_DOCUMENT_DENIED");
  var row=solutions.selectById(id);if(row==null||Boolean.TRUE.equals(row.getDeleted())||!Objects.equals(tenant(),row.getTenantId()))throw denied("SOLUTION_CUSTOMER_DOCUMENT_DENIED");
  String action=write?ProjectScopeApi.ACTION_MANAGE:ProjectScopeApi.ACTION_VIEW;var scope=scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant(),actor,row.getProjectId(),action));if(!visible(scope,row.getProjectId()))throw denied("SOLUTION_CUSTOMER_DOCUMENT_DENIED");
  if(write){var checked=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant(),actor,row.getProjectId(),action,scope.treeVersion()));if(!visible(checked,row.getProjectId())||!Objects.equals(scope.treeVersion(),checked.treeVersion()))throw denied("SOLUTION_CUSTOMER_DOCUMENT_DENIED");var locked=solutions.selectResultForUpdate(new SolutionResultLockQuery(tenant(),row.getProjectId(),id));if(locked==null||!Objects.equals(row.getProjectId(),locked.getProjectId()))throw denied("SOLUTION_CUSTOMER_DOCUMENT_DENIED");row=locked;var query=new ProjectAcceptanceContextApi.Query(tenant(),row.getProjectId(),actor);var project=projects.inspect(query);if(project==null)throw denied("SOLUTION_CUSTOMER_DOCUMENT_DENIED");project=projects.lock(query,project.projectVersion(),scope.treeVersion());if(project==null||!Objects.equals(row.getProjectId(),project.projectId())||!Objects.equals(scope.treeVersion(),project.treeVersion())||!"ACTIVE".equals(project.lifecycleStatus()))throw denied("SOLUTION_CUSTOMER_DOCUMENT_DENIED");}
  return row;
 }
 private static boolean visible(ProjectScopeResult s,Long id){return s!=null&&s.treeVersion()!=null&&s.fullProjectIds()!=null&&s.fullProjectIds().contains(id);}
 private static Long tenant(){return TenantContextHolder.getRequiredTenantId();}
 private static Map<String,Object> envelope(String raw){if(raw==null||raw.isBlank())return new LinkedHashMap<>();try{Map<String,Object> parsed=JsonUtils.parseObject(raw,Map.class);return parsed==null?new LinkedHashMap<>():new LinkedHashMap<>(parsed);}catch(RuntimeException invalid){return new LinkedHashMap<>();}}
 private static BusinessContractException denied(String code){return new BusinessContractException(code,"Customer solution document requires actual draft owner, version and authorized file proof");}
}
