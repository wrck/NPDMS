package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.business;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.business.AcceptanceBusinessDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.business.AcceptanceBusinessMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceActivityIdentityLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.*;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class AcceptanceBusinessService extends DefaultProjectBusinessService<AcceptanceBusinessMapper,AcceptanceBusinessDO> {
    private final AcceptanceActivityMapper activities;
    private final ProjectAcceptanceContextApi projects;
    private final AcceptanceReportCommandService reports;
    private final AcceptanceReportQueryService queries;
    private final AcceptanceReportBusinessResultSource results;
    private final cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper versions;
    private final cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi files;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    private static final String WRITE="pms:acceptance:report:write";
    @Override public Set<String> runtimeActions(cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi.UserContext context){
        var actions=new HashSet<>(super.runtimeActions(context));
        // Native report files retain their original report-write + PROJECT_EDIT + PLT upload policy.
        // The optional common attachment capability does not govern report-version attachments.
        if(permissions.hasAnyPermissions(context.userId(),WRITE) && permissions.hasAnyPermissions(context.userId(),"pms:file:upload")){
            try{
                var project=projects.inspect(new ProjectAcceptanceContextApi.Query(context.tenantId(),context.projectId(),context.userId()));
                if(project!=null && Objects.equals(project.projectId(),context.projectId()))actions.add("FILE_WRITE");
            }catch(RuntimeException denied){/* Keep readable report history without granting file writes. */}
        }
        return Set.copyOf(actions);
    }
    @Override protected Long generatedId(){return com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();}
    @Override protected List<BusinessOperationDescriptor> configureOperations(List<BusinessOperationDescriptor> operations){return operations.stream().filter(op->!"delete".equals(op.code())).map(op->new BusinessOperationDescriptor(op.code(),op.version(),op.name(),op.kind(),"configure-fields".equals(op.code())?op.authorizationPolicyRef():WRITE)).toList();}
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix){return List.of(operation("report-create","创建报告草稿"),operation("report-save","保存报告草稿"),operation("report-publish","发布验收报告"),operation("report-revoke","撤销当前报告"));}
    private BusinessOperationDescriptor operation(String code,String name){return new BusinessOperationDescriptor(code,1,name,BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,WRITE);}
    public record Initial(Long expectedProjectVersion,Long expectedTreeVersion){ }
    @Override protected void beforeCreate(AcceptanceBusinessDO row){row.setActivityStatus("PENDING");row.setOriginKind("DIRECT");row.setRuleSnapshot(IndependentAcceptancePolicy.SNAPSHOT);}
    @Override protected void beforeFormWrite(AcceptanceBusinessDO before,AcceptanceBusinessDO row,Map<String,Object> values){
        if(values.keySet().stream().anyMatch(key->!Set.of("expectedProjectVersion","expectedTreeVersion").contains(key)))throw invalid("验收命令字段无效");
        if(before!=null)return;
        var input=JsonUtils.parseObject(JsonUtils.toJsonString(values),Initial.class);
        if(input.expectedProjectVersion()==null||input.expectedTreeVersion()==null)throw invalid("缺少项目与权限树版本，请重新读取项目");
        var context=projects.lock(new ProjectAcceptanceContextApi.Query(actor().tenantId(),row.getProjectId(),actor().userId()),input.expectedProjectVersion(),input.expectedTreeVersion());
        if(context==null||!"ACTIVE".equals(context.lifecycleStatus())||!Objects.equals(row.getProjectId(),context.projectId()))throw invalid("项目未处于执行中");
        if(activities.selectByIdentityForUpdate(new AcceptanceActivityIdentityLockQuery(actor().tenantId(),row.getProjectId(),row.getAcceptanceType()))!=null)throw invalid("此项目已创建该类验收，请打开已有活动");
        row.setOriginKey(actor().userId()+":"+UUID.randomUUID());
        row.setOriginSnapshot(JsonUtils.toJsonString(Map.of("projectId",context.projectId(),"rootProjectId",context.rootProjectId(),"projectVersion",context.projectVersion(),"treeVersion",context.treeVersion(),"actorId",actor().userId())));
    }
    @Override protected void validateBusiness(AcceptanceBusinessDO row){if(row.getOriginSnapshot()==null||row.getOriginKey()==null)throw invalid("验收来源与资格未冻结");}
    @Override protected void beforeUpdate(AcceptanceBusinessDO before,AcceptanceBusinessDO row){mutable(before);if(!Objects.equals(before.getProjectId(),row.getProjectId())||!Objects.equals(before.getAcceptanceType(),row.getAcceptanceType()))throw invalid("验收类型及所属项目不可修改");}
    @Override protected void beforeDelete(AcceptanceBusinessDO row){throw invalid("验收活动与报告历史必须保留");}
    @Override protected void validateDelivery(AcceptanceBusinessDO row,boolean write){if(write)mutable(row);}
    @Override protected void validateApproval(AcceptanceBusinessDO row,boolean write){if(write)mutable(row);}
    @Override protected Map<String,Object> formContext(AcceptanceBusinessDO row){return Map.of("reports",queries.listVersions(row.getId(),queryActor()));}
    @Override protected Map<String,Object> approvalValues(AcceptanceBusinessDO row){var values=new LinkedHashMap<>(super.approvalValues(row));values.put("reports",queries.listVersions(row.getId(),queryActor()));return values;}
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","REPORT_VERSION_PUBLISHED","当前验收报告已发布","PRELIMINARY_ACCEPTANCE_PASSED","当前初验报告有效且通过","FINAL_ACCEPTANCE_PASSED","当前终验报告有效且通过");}
    @Override protected RuntimeResult runtimeResult(AcceptanceBusinessDO row){
        if(row.getCurrentReportVersionId()==null)return new RuntimeResult(reportFacts(row,false,false),false,row.getUpdateTime());
        var query=new cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Query(row.getTenantId(),row.getProjectId(),AcceptanceReportBusinessResultSource.TYPE,row.getId().toString(),row.getCurrentReportVersionId().toString());
        var fact=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()?results.lockAndInspect(query):results.inspect(query);
        boolean published=fact.result()!=null&&fact.result().validity()==cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity.CURRENT;
        var report=published?versions.selectById(row.getCurrentReportVersionId()):null;
        boolean passed=report!=null&&"PASS".equals(report.getConclusionCode());
        return new RuntimeResult(reportFacts(row,published,passed),passed&&"COMPLETED".equals(row.getActivityStatus()),fact.result()==null?row.getUpdateTime():fact.result().formedAt(),fact.result()==null?fact.status().name():fact.result().observationVersion());
    }
    private Map<String,Boolean> reportFacts(AcceptanceBusinessDO row,boolean published,boolean passed){return Map.of("BUSINESS_RECORD_SAVED",true,"REPORT_VERSION_PUBLISHED",published,"PRELIMINARY_ACCEPTANCE_PASSED",passed&&"PRELIMINARY".equals(row.getAcceptanceType()),"FINAL_ACCEPTANCE_PASSED",passed&&"FINAL".equals(row.getAcceptanceType()));}
    public BusinessOperationReceipt createReport(Long id,Long version,String key){return businessAction("report-create",id,version,key,Map.of(),ReceiptOutcome.SAVED,row->{mutable(row);reports.createDraft(new AcceptanceReportCommands.CreateDraftCommand(id,version,emptyContent()),commandActor());persistBusinessChange(row,copy(row),"report-create");return mapper.selectById(id);});}
    public List<cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact> draftFiles(Long id,Long reportId){
        get(id);var report=versions.selectById(reportId);
        if(report==null||!Objects.equals(report.getTenantId(),actor().tenantId())||!Objects.equals(report.getAcceptanceId(),id)||!"DRAFT".equals(report.getReportStatus()))throw invalid("报告草稿不属于当前验收活动");
        var key=new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey("ACC","ACCEPTANCE_REPORT_VERSION",reportId.toString(),"ACCEPTANCE_REPORT_ATTACHMENT");
        return files.inspectReferenceSets(new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery(List.of(key),cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes.READ)).stream().flatMap(set->set.activeFacts().stream()).toList();
    }
    public BusinessOperationReceipt saveReport(Long id,Long version,String key,Long reportId,Integer reportNo){return businessAction("report-save",id,version,key,Map.of("reportId",reportId,"reportNo",reportNo),ReceiptOutcome.SAVED,row->{mutable(row);reports.updateDraft(new AcceptanceReportCommands.UpdateDraftCommand(id,reportId,version,reportNo,emptyContent()),commandActor());persistBusinessChange(row,copy(row),"report-save");return mapper.selectById(id);});}
    public BusinessOperationReceipt publishReport(Long id,Long version,String key,Long reportId,Integer reportNo,Long expectedCurrent){
        var intent=new LinkedHashMap<String,Object>();intent.put("reportId",reportId);intent.put("reportNo",reportNo);intent.put("expectedCurrent",expectedCurrent);
        return businessAction("report-publish",id,version,key,intent,ReceiptOutcome.SAVED,row->{mutable(row);reports.updateDraft(new AcceptanceReportCommands.UpdateDraftCommand(id,reportId,version,reportNo,emptyContent()),commandActor());reports.publish(new AcceptanceReportCommands.PublishCommand(id,reportId,version,reportNo,expectedCurrent,key,digest(intent)),commandActor());return mapper.selectById(id);});
    }
    public BusinessOperationReceipt revokeReport(Long id,Long version,String key,Long reportId,Integer reportNo){var intent=Map.<String,Object>of("reportId",reportId,"reportNo",reportNo);return businessAction("report-revoke",id,version,key,intent,ReceiptOutcome.SAVED,row->{mutable(row);reports.revoke(new AcceptanceReportCommands.RevokeCommand(id,version,reportId,reportNo,key,digest(intent)),commandActor());return mapper.selectById(id);});}
    private void mutable(AcceptanceBusinessDO row){IndependentAcceptancePolicy.requireMutable(BeanUtils.toBean(row,AcceptanceActivityDO.class));}
    private AcceptanceReportCommands.DraftContent emptyContent(){return new AcceptanceReportCommands.DraftContent(null,null,null,null);}
    private AcceptanceReportQueryService.Actor queryActor(){return new AcceptanceReportQueryService.Actor(actor().tenantId(),actor().userId());}
    private AcceptanceReportCommands.Actor commandActor(){
        var caller=actor();String correlation=caller.correlationId();
        return new AcceptanceReportCommands.Actor(caller.tenantId(),caller.userId(),correlation==null||correlation.isBlank()?UUID.randomUUID().toString():correlation);
    }
    private String digest(Object value){return cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(value));}
    private BusinessContractException invalid(String message){return new BusinessContractException("ACCEPTANCE_INVALID",message);}
}
