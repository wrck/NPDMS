package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.business;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.*;
import cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessOperationDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt;
import cn.iocoder.yudao.module.pms.platform.support.business.DefaultProjectBusinessService;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** Survey differences only. CRUD, scope, concurrency, receipts and delivery APIs are inherited. */
@Service @RequiredArgsConstructor
public class SiteSurveyBusinessService extends DefaultProjectBusinessService<SiteSurveyEntityMapper,SiteSurveyEntityDO> {
    private final EngineeringRecordCodeGenerator codes;
    private final SiteSurveyDetails details;
    private final SiteSurveyEntityFormService forms;
    private final SiteSurveyEntityWriteAccess access;
    private final EngineeringRuleReevaluationEvents rules;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService> locations;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateApi> deadlines;

    @Override protected Map<String,String> runtimeFactLabels(){return Map.of("SURVEY_CONFIRMED","工勘已确认","SURVEY_ARCHIVED","工勘已归档");}
    @Override protected Map<String,Boolean> runtimeFacts(SiteSurveyEntityDO row){
        if(row.getStatus()==null || !Set.of(0,1,2,3).contains(row.getStatus()))throw exception(SITE_SURVEY_STATUS_INVALID);
        return Map.of("SURVEY_CONFIRMED",Set.of(1,3).contains(row.getStatus()),"SURVEY_ARCHIVED",Integer.valueOf(3).equals(row.getStatus()));
    }
    @Override protected boolean runtimeHandlingCompleted(SiteSurveyEntityDO row){return Boolean.TRUE.equals(runtimeFacts(row).get("SURVEY_CONFIRMED"));}
    @Override protected LocalDateTime runtimeFormedAt(SiteSurveyEntityDO row){return row.getConfirmedAt();}
    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
        return List.of(operation("confirm","确认工勘",prefix),operation("reject","驳回工勘",prefix),operation("archive","归档工勘",prefix),operation("location","维护工勘地点",prefix),operation("deadline","更新项目期限",prefix));
    }
    private BusinessOperationDescriptor operation(String code,String title,String prefix) {
        return new BusinessOperationDescriptor(code,1,title,BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update");
    }
    @Override protected <T>T inBusinessOperation(String operation,SiteSurveyEntityDO current,SiteSurveyEntityDO proposed,Supplier<T> work) {
        var row=current==null?proposed:current;var actor=actor();
        String nativeOperation="SOL.SITE_SURVEY."+(Set.of("save","save-form").contains(operation)?"UPDATE":operation.toUpperCase(Locale.ROOT));
        String permission="pms:sol-site-survey:"+(operation.equals("create")?"create":operation.equals("delete")?"delete":"update");
        return ProjectOwnerOperationScope.call("SOL","SITE_SURVEY",()->new ProjectOwnerOperationScope.Declaration(
                actor.tenantId(),actor.userId(),row.getProjectId(),"SOL","SITE_SURVEY",nativeOperation,1,
                current==null?null:current.getId().toString(),null),()->{
            access.lock(row.getProjectId(),permission,null,nativeOperation,current==null?null:current.getId());return work.get();
        });
    }
    @Override protected cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef formTarget(SiteSurveyEntityDO row){
        return cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef.current(new cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef(row.getTenantId(),"SOL","SITE_SURVEY",row.getId()));
    }
    @Override protected cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey formConfigurationProvider(){
        return new cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormProviderKey("SOL","SITE_SURVEY");
    }
    @Override protected String formConfigurationCategory(){return "SITE_SURVEY";}
    @Override protected Map<String,String> formConfigurationBindings(cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormRevisionFact schema){return forms.fieldBindings(schema);}
    @Override protected cn.iocoder.yudao.module.pms.platform.support.business.BusinessFormData defaultForm(Long projectId){
        var schema=forms.defaultSchema();
        var layout=new cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi.Layout(
                new cn.iocoder.yudao.module.pms.platform.api.entity.EntityFormApi.Binding(schema.templateRevisionId(),null,forms.fieldBindings(schema),0),
                schema.templateId(),schema.revisionNo(),schema.revisionFactVersion(),schema.engineCode(),schema.designerVersion(),schema.rendererVersion(),
                schema.formConfJson(),schema.formRulesJson(),schema.fields());
        return new cn.iocoder.yudao.module.pms.platform.support.business.BusinessFormData(layout,
                new cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.Values(null,Map.of(),0),List.of(),Map.of("projectId",projectId));
    }
    @Override protected Map<String,Object> formContext(SiteSurveyEntityDO row){
        // Presentation-only references use the existing authorized form endpoint, never public body fields.
        var context=new LinkedHashMap<String,Object>();
        context.put("outsourceRequestId",row.getOutsourceRequestId());
        context.put("addressId",row.getAddressId());context.put("addressVersion",row.getAddressVersion());
        context.put("siteId",row.getSiteId());context.put("siteVersion",row.getSiteVersion());
        context.put("siteLocationId",row.getSiteLocationId());context.put("siteLocationVersion",row.getSiteLocationVersion());
        return context;
    }
    @Override protected void beforeFormWrite(SiteSurveyEntityDO before,SiteSurveyEntityDO row,Map<String,Object> business){
        if(business.isEmpty())return;
        if(!Set.of("requiredEndDate","projectVersion").containsAll(business.keySet())
                || !business.containsKey("requiredEndDate") || !business.containsKey("projectVersion"))
            throw new BusinessContractException("FIELD_NOT_OPEN","Unsupported survey form command");
        if(before!=null)requireState(before,0);
        final java.time.LocalDate date;final Long version;
        try{date=java.time.LocalDate.parse(Objects.toString(business.get("requiredEndDate"),""));version=Long.valueOf(Objects.toString(business.get("projectVersion"),""));}
        catch(RuntimeException invalid){throw exception(SITE_SURVEY_FORM_INVALID);}
        if(version<0)throw exception(SITE_SURVEY_FORM_INVALID);
        var port=deadlines.getIfAvailable();if(port==null)throw new BusinessContractException("CAPABILITY_UNAVAILABLE","项目期限服务未装配");
        port.updateFromSurvey(new cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateCommand(actor().tenantId(),actor().userId(),row.getProjectId(),version,date));
        row.setRequiredEndDate(date);
    }
    @Override protected Long generatedId(){return com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();}
    @Override protected void afterRead(SiteSurveyEntityDO row){details.load(row);}
    @Override protected void beforeCreate(SiteSurveyEntityDO row) {
        row.setCode(codes.next(row.getProjectId(),EngineeringRecordCodeGenerator.SITE_SURVEY,mapper));
        row.setStatus(0);row.setOutsourceRequired(Boolean.TRUE.equals(row.getOutsourceRequired()));
        requireLocation(row);row.setLocationResolutionStatus("UNRESOLVED");
    }
    @Override protected void beforeUpdate(SiteSurveyEntityDO before,SiteSurveyEntityDO row) {
        requireState(before,0);
        if(!Objects.equals(before.getProjectId(),row.getProjectId()))throw exception(SITE_SURVEY_FORM_INVALID);
        if(!Objects.equals(before.getLocation(),row.getLocation())) {
            if(before.getAddressId()!=null)throw new BusinessContractException("STRUCTURED_LOCATION_REQUIRED","已有结构化地点，请通过地点维护操作修改");
            requireLocation(row);row.setLocationResolutionStatus("UNRESOLVED");
        }
    }
    @Override protected void validateBusiness(SiteSurveyEntityDO row,String operation) {
        var failures=defaults.validator().validate(BeanUtils.toBean(row,SiteSurveyEntitySaveReqVO.class));
        if(!failures.isEmpty())throw exception(SITE_SURVEY_FORM_INVALID);
        requireLocation(row);forms.validateInheritedSave(row,"create".equals(operation));
    }
    @Override protected void afterCreate(SiteSurveyEntityDO row) {
        var actor=actor();ProjectOwnerOperationScope.registerCreated(actor.tenantId(),actor.userId(),row.getProjectId(),"SOL","SITE_SURVEY","SOL.SITE_SURVEY.CREATE",1,null,row.getId().toString());
        details.save(row,actor.userId());changed(row);
    }
    @Override protected void afterUpdate(SiteSurveyEntityDO before,SiteSurveyEntityDO row){details.save(row,actor().userId());changed(row);}
    @Override protected void beforeDelete(SiteSurveyEntityDO row){requireState(row,0);if(row.getOutsourceRequestId()!=null)throw exception(SITE_SURVEY_OUTSOURCE_DELETE_BLOCKED);}
    @Override protected void afterDelete(SiteSurveyEntityDO row){changed(row);}
    @Override protected void afterChange(String operation,SiteSurveyEntityDO before,SiteSurveyEntityDO row){if("save-form".equals(operation))details.save(row,actor().userId());changed(row);}
    @Override protected void validateDelivery(SiteSurveyEntityDO row,boolean write){if(write && !Objects.equals(row.getStatus(),0) && !Objects.equals(row.getStatus(),1))throw exception(SITE_SURVEY_STATUS_INVALID);}
    public BusinessOperationReceipt confirm(Long id,Long version,String key){return change("confirm",id,version,key,Map.of(),row->{requireState(row,0);forms.validate(row,false);row.setStatus(1);row.setConfirmedAt(LocalDateTime.now());});}
    public BusinessOperationReceipt reject(Long id,Long version,String key){return change("reject",id,version,key,Map.of(),row->{requireState(row,0);row.setStatus(2);});}
    public BusinessOperationReceipt archive(Long id,Long version,String key){return change("archive",id,version,key,Map.of(),row->{requireState(row,1);row.setStatus(3);row.setArchivedAt(LocalDateTime.now());});}
    public BusinessOperationReceipt maintainLocation(Long id,Long version,String key,String description,
            cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand command){
        if(command==null || description==null || description.isBlank())throw exception(SITE_SURVEY_LOCATION_REQUIRED);
        return change("location",id,version,key,Map.of("description",description,"location",command),row->{
            requireState(row,0);var port=locations.getIfAvailable();
            if(port==null)throw new BusinessContractException("CAPABILITY_UNAVAILABLE","地点维护服务未装配");
            var fact=port.maintain(row.getProjectId(),"SITE_SURVEY",row.getId(),version,description,command);
            if(fact==null || !"RESOLVED".equals(fact.resolutionStatus()))throw exception(SITE_SURVEY_LOCATION_INVALID);
            row.setLocation(description);row.setAddressId(fact.addressId());row.setAddressVersion(fact.addressVersion());
            row.setSiteId(fact.siteId());row.setSiteVersion(fact.siteVersion());row.setSiteLocationId(fact.siteLocationId());row.setSiteLocationVersion(fact.siteLocationVersion());
            row.setLocationResolutionStatus(fact.resolutionStatus());row.setAddressSnapshot(fact.addressSnapshot());row.setLocationSnapshot(fact.locationSnapshot());
        });
    }
    public BusinessOperationReceipt updateDeadline(Long id,Long version,String key,Long projectVersion,java.time.LocalDate endDate){
        if(projectVersion==null || projectVersion<0 || endDate==null)throw exception(SITE_SURVEY_FORM_INVALID);
        return change("deadline",id,version,key,Map.of("projectVersion",projectVersion,"endDate",endDate),row->{
            requireState(row,0);var port=deadlines.getIfAvailable();
            if(port==null)throw new BusinessContractException("CAPABILITY_UNAVAILABLE","项目期限服务未装配");
            port.updateFromSurvey(new cn.iocoder.yudao.module.pms.project.api.deadline.ProjectEndDateCommand(actor().tenantId(),actor().userId(),row.getProjectId(),projectVersion,endDate));
            row.setRequiredEndDate(endDate);
        });
    }
    private void changed(SiteSurveyEntityDO row){rules.changed(row.getProjectId(),"SiteSurvey",row.getId(),actor().userId(),"site-survey:"+row.getId()+":"+row.getVersion());}
    private void requireState(SiteSurveyEntityDO row,int state){if(!Objects.equals(row.getStatus(),state))throw exception(SITE_SURVEY_STATUS_INVALID);}
    private void requireLocation(SiteSurveyEntityDO row){if(row.getLocation()==null||row.getLocation().isBlank())throw exception(SITE_SURVEY_LOCATION_REQUIRED);}
}
