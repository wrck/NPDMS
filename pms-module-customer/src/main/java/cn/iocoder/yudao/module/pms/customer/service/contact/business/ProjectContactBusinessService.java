package cn.iocoder.yudao.module.pms.customer.service.contact.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.business.ProjectContactBusinessDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.business.ProjectContactBusinessMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.query.*;
import cn.iocoder.yudao.module.pms.customer.service.contact.*;
import cn.iocoder.yudao.module.pms.customer.service.security.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.support.business.*;
import cn.iocoder.yudao.module.pms.project.api.contact.ProjectContactContextApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/** Shared persistence with the existing CUS source, primary-contact, scope and history rules. */
@Service @RequiredArgsConstructor
public class ProjectContactBusinessService extends DefaultProjectBusinessService<ProjectContactBusinessMapper,ProjectContactBusinessDO> {
    private final ProjectContactContextApi projects;
    private final CustomerContactMasterMapper sources;
    private final ProjectCustomerContactMapper contacts;
    private final ContactHistoryMapper history;
    private final ProjectCustomerContactService nativeService;
    private final ProjectContactRuleWakeup wakeup;
    private final CustomerContactAccessService contactAccess;
    private final CustomerFieldMaskingService masking;
    private final ThreadLocal<Boolean> removalAcknowledged=new ThreadLocal<>();

    @Override protected List<BusinessOperationDescriptor> configureOperations(List<BusinessOperationDescriptor> operations){
        // Native project contacts are authorized by project visibility + the current contact-management context.
        return operations.stream().map(op->new BusinessOperationDescriptor(op.code(),op.version(),op.name(),op.kind(),
                "configure-fields".equals(op.code())?op.authorizationPolicyRef():"pms:project:query")).toList();
    }
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix){return List.of(new BusinessOperationDescriptor("restore",1,"恢复联系人",BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,"pms:project:query"));}
    @Override protected void beforeCreate(ProjectContactBusinessDO row){if(row.getStatus()==null)row.setStatus(0);if(row.getPrimaryFlag()==null)row.setPrimaryFlag(false);}
    @Override protected void beforeUpdate(ProjectContactBusinessDO before,ProjectContactBusinessDO row){if(!Objects.equals(before.getProjectId(),row.getProjectId()))throw denied("联系人不能迁移到其他项目");}
    public record ContactCommand(Long expectedProjectVersion,Long sourceContactId,Boolean confirmNoPrimary) { }
    @Override protected void beforeFormWrite(ProjectContactBusinessDO before,ProjectContactBusinessDO row,Map<String,Object> values){
        if(values.keySet().stream().anyMatch(key->!Set.of("expectedProjectVersion","sourceContactId","confirmNoPrimary").contains(key)))throw denied("联系人命令字段无效");
        var command=JsonUtils.parseObject(JsonUtils.toJsonString(values),ContactCommand.class);
        Long projectVersion=command.expectedProjectVersion();
        if(projectVersion==null && before!=null && Objects.equals(values(before),values(row)) && Objects.equals(before.getStatus(),row.getStatus()) && Objects.equals(before.getPrimaryFlag(),row.getPrimaryFlag()))projectVersion=context(row.getProjectId()).projectVersion();
        var context=lock(row.getProjectId(),projectVersion,before==null || Objects.equals(row.getStatus(),0) || Boolean.TRUE.equals(row.getPrimaryFlag()));
        if(before!=null && !Objects.equals(before.getCustomerId(),context.customerId()))throw denied("联系人不属于项目当前客户");
        if(before==null){
            var primary=contacts.selectPrimaryForUpdate(new ProjectContactRowQuery(actor().tenantId(),row.getProjectId(),null));
            row.setPrimaryFlag(Boolean.TRUE.equals(row.getPrimaryFlag()) || primary==null && Objects.equals(row.getStatus(),0));
            if(Boolean.TRUE.equals(row.getPrimaryFlag()))ContactRules.requirePrimaryAllowed(row.getStatus(),true,null,null);
            CustomerContactMasterDO source;
            if(command.sourceContactId()!=null){
                source=source(context.customerId(),command.sourceContactId());
                if(contacts.selectSourceIncludingDeletedForUpdate(new ProjectContactSourceQuery(actor().tenantId(),row.getProjectId(),source.getId()))!=null)throw denied("该来源已被本项目引用，请维护或恢复原记录");
            }else{
                normalize(row);ContactRules.normalize(values(row),0);
                source=new CustomerContactMasterDO();source.setTenantId(actor().tenantId());source.setCustomerId(context.customerId());source.setVersion(0L);
                source.setName(row.getName());source.setDepartment(row.getDepartment());source.setTitle(row.getTitle());source.setMobile(row.getMobile());source.setPhone(row.getPhone());source.setEmail(row.getEmail());source.setStatus(0);source.setPrimaryFlag(false);
                sources.insert(source);append(source.getId(),null,null,"CREATE",null,source);
            }
            row.setCustomerId(context.customerId());row.setCustomerContactId(source.getId());
            if(Boolean.TRUE.equals(row.getPrimaryFlag()))demote(row.getProjectId(),primary,null);
        }else{
            if(command.sourceContactId()!=null && !Objects.equals(command.sourceContactId(),before.getCustomerContactId()))throw denied("项目联系人来源不能替换");
            if(Objects.equals(row.getStatus(),0) || Boolean.TRUE.equals(row.getPrimaryFlag()))source(context.customerId(),before.getCustomerContactId());
            if(Boolean.TRUE.equals(row.getPrimaryFlag())){
                ContactRules.requirePrimaryAllowed(row.getStatus(),true,null,null);
                demote(row.getProjectId(),contacts.selectPrimaryForUpdate(new ProjectContactRowQuery(actor().tenantId(),row.getProjectId(),null)),row.getId());
            }
            if(Boolean.TRUE.equals(before.getPrimaryFlag()) && !Objects.equals(row.getStatus(),0))ContactRules.requireRemovalAllowed(true,Boolean.TRUE.equals(command.confirmNoPrimary()));
        }
        normalize(row);
        if(Boolean.TRUE.equals(row.getPrimaryFlag()) && (before==null || !Boolean.TRUE.equals(before.getPrimaryFlag())))row.setPrimarySetTime(LocalDateTime.now());
        if(!Boolean.TRUE.equals(row.getPrimaryFlag()))row.setPrimarySetTime(null);
        row.setContactValidated(true);
    }
    @Override protected void validateBusiness(ProjectContactBusinessDO row){if(!row.isContactValidated())throw denied("请提供项目版本和联系人保存内容");}
    @Override protected void afterSave(ProjectContactBusinessDO before,ProjectContactBusinessDO row,String operation){append(row.getCustomerContactId(),row.getProjectId(),row.getId(),before==null?"REFERENCE":"UPDATE",nativeRow(before),nativeRow(row));}
    @Override protected void beforeDelete(ProjectContactBusinessDO row){
        var current=context(row.getProjectId());lock(row.getProjectId(),current.projectVersion(),false);
        ContactRules.requireRemovalAllowed(Boolean.TRUE.equals(row.getPrimaryFlag()),Boolean.TRUE.equals(removalAcknowledged.get()));
        if(mapper.markDeletion(new ProjectContactBusinessMapper.DeletionAudit(actor().tenantId(),row.getId(),row.getVersion(),actor().userId()))!=1)throw denied("联系人已被修改");
    }
    @Override protected void afterDelete(ProjectContactBusinessDO row){var before=nativeRow(row);before.setVersion(Math.toIntExact(row.getVersion()-1));append(row.getCustomerContactId(),row.getProjectId(),row.getId(),"DELETE",before,null);}
    public BusinessOperationReceipt deleteAcknowledged(Long id,Long version,String key,boolean confirmed){removalAcknowledged.set(confirmed);try{return super.delete(id,version,key);}finally{removalAcknowledged.remove();}}
    public BusinessOperationReceipt restore(Long id,Long version,String key,Long projectId,Long projectVersion,int status){
        return write("restore",id,version,Map.of("projectId",projectId,"projectVersion",projectVersion,"status",status),key,actor->{
            nativeService.restore(new CustomerContactMasterService.Actor(actor.tenantId(),actor.userId()),projectId,id,Math.toIntExact(projectVersion),Math.toIntExact(version),status);
            var row=get(id);return new BusinessOperationReceipt(ReceiptOutcome.SAVED,identity(row),row.getVersion(),List.of(),null,null,"restore",1);
        });
    }
    @Override protected PageResult<ProjectContactBusinessDO> selectPage(BusinessReadQuery query){
        if(query.criteria().getProjectId()==null)throw denied("请选择联系人所属项目");
        var context=context(query.criteria().getProjectId());
        if(context.canViewHistory())return super.selectPage(query);
        var criteria=BeanUtils.toBean(query.criteria(),BusinessPageQuery.class);
        var filters=new ArrayList<>(criteria.getFilters()==null?List.<BusinessFieldFilter>of():criteria.getFilters());
        filters.add(new BusinessFieldFilter("status",BusinessFieldFilter.Operator.EQ,List.of(0)));
        criteria.setFilters(filters);return super.selectPage(new BusinessReadQuery(query.tenantId(),query.projectIds(),criteria));
    }
    @Override protected void afterRead(ProjectContactBusinessDO row){if(!context(row.getProjectId()).canViewHistory() && !Objects.equals(row.getStatus(),0))throw denied("无权查看历史联系人");}
    @Override public Map<String,Object> readableValues(ProjectContactBusinessDO row){var values=new LinkedHashMap<>(super.readableValues(row));if(!context(row.getProjectId()).canManage() && contactAccess.resolve(actor().userId(),true)!=CustomerFieldMaskingService.ContactAccess.RAW)mask(values);return values;}
    @Override protected Map<String,Object> approvalValues(ProjectContactBusinessDO row){var values=new LinkedHashMap<>(super.readableValues(row));mask(values);return values;}
    @Override protected void validateDelivery(ProjectContactBusinessDO row,boolean write){if(write && !context(row.getProjectId()).canManage())throw denied("无权维护项目联系人");}
    @Override protected void validateApproval(ProjectContactBusinessDO row,boolean write){validateDelivery(row,write);}
    @Override protected Map<String,Object> formContext(ProjectContactBusinessDO row){return Map.of("project",context(row.getProjectId()));}
    @Override protected BusinessFormData defaultForm(Long projectId){return new BusinessFormData(null,new cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.Values(null,Map.of(),0),List.of(),Map.of("project",context(projectId)));}
    @Override protected boolean runtimeHandlingCompleted(ProjectContactBusinessDO row){return Objects.equals(row.getStatus(),0);}
    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("BUSINESS_RECORD_SAVED","业务记录已保存","PROJECT_CONTACT_ACTIVE","项目联系人有效");}
    @Override protected Map<String,Boolean> runtimeFacts(ProjectContactBusinessDO row){return Map.of("BUSINESS_RECORD_SAVED",true,"PROJECT_CONTACT_ACTIVE",runtimeHandlingCompleted(row));}
    private ProjectContactContextApi.Context context(Long project){return projects.inspect(new ProjectContactContextApi.Query(actor().tenantId(),actor().userId(),project));}
    private ProjectContactContextApi.Context lock(Long project,Long version,boolean requireEnabled){
        var current=projects.lockForWrite(new ProjectContactContextApi.WriteQuery(actor().tenantId(),actor().userId(),project,version));
        if(current.customerId()==null)throw denied("项目尚未关联客户");
        var customer=sources.selectCustomerForUpdate(new ContactCustomerReferenceQuery(actor().tenantId(),current.customerId()));
        if(customer==null || requireEnabled && !"ENABLED".equals(customer.getLifecycleStatus()))throw denied("项目客户不可用");return current;
    }
    private CustomerContactMasterDO source(Long customer,Long id){var row=sources.selectForUpdate(new ContactMasterRowQuery(actor().tenantId(),customer,id));if(row==null || !Objects.equals(row.getStatus(),0))throw denied("来源联系人不可用");return row;}
    private void demote(Long project,ProjectCustomerContactDO row,Long keep){if(row==null || Objects.equals(row.getId(),keep))return;var before=BeanUtils.toBean(row,ProjectCustomerContactDO.class);row.setPrimaryFlag(false);row.setPrimarySetTime(null);if(contacts.updateById(row)!=1)throw denied("主联系人已变化");append(row.getCustomerContactId(),project,row.getId(),"UPDATE",before,row);}
    private ContactValues values(ProjectContactBusinessDO row){return new ContactValues(row.getName(),row.getDepartment(),row.getTitle(),row.getMobile(),row.getPhone(),row.getEmail(),row.getRemark());}
    private void normalize(ProjectContactBusinessDO row){var value=ContactRules.normalize(values(row),row.getStatus());row.setName(value.name());row.setDepartment(value.department());row.setTitle(value.title());row.setMobile(value.mobile());row.setPhone(value.phone());row.setEmail(value.email());row.setRemark(value.remark());}
    private ProjectCustomerContactDO nativeRow(ProjectContactBusinessDO row){if(row==null)return null;var nativeRow=BeanUtils.toBean(row,ProjectCustomerContactDO.class);nativeRow.setVersion(Math.toIntExact(row.getVersion()));return nativeRow;}
    private void append(Long source,Long project,Long relation,String action,Object before,Object after){var entry=new ContactHistoryDO();entry.setTenantId(actor().tenantId());entry.setActorUserId(actor().userId());entry.setCustomerContactId(source);entry.setProjectId(project);entry.setProjectRelationId(relation);entry.setActionCode(action);entry.setBeforeValues(before==null?null:JsonUtils.toJsonString(before));entry.setAfterValues(after==null?null:JsonUtils.toJsonString(after));entry.setOccurredAt(LocalDateTime.now());history.insert(entry);if(project!=null)wakeup.changed(actor().tenantId(),project,actor().userId());}
    private void mask(Map<String,Object> values){values.put("mobile",masking.maskPhone((String)values.get("mobile")));values.put("phone",masking.maskPhone((String)values.get("phone")));values.put("email",masking.maskEmail((String)values.get("email")));}
    private BusinessContractException denied(String reason){return new BusinessContractException("CONTACT_VALUES_INVALID",reason);}
}
