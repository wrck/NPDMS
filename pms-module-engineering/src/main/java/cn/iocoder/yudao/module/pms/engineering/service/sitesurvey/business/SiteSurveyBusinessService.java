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

    @Override protected List<BusinessOperationDescriptor> businessOperations(String prefix) {
        return List.of(operation("confirm","确认工勘",prefix),operation("reject","驳回工勘",prefix),operation("archive","归档工勘",prefix));
    }
    private BusinessOperationDescriptor operation(String code,String title,String prefix) {
        return new BusinessOperationDescriptor(code,1,title,BusinessOperationDescriptor.StandardOperationKind.DOMAIN_COMMAND,prefix+":update");
    }
    @Override protected <T>T inBusinessOperation(String operation,SiteSurveyEntityDO current,SiteSurveyEntityDO proposed,Supplier<T> work) {
        var row=current==null?proposed:current;var actor=actor();
        String nativeOperation="SOL.SITE_SURVEY."+(operation.equals("save")?"UPDATE":operation.toUpperCase(Locale.ROOT));
        String permission="pms:sol-site-survey:"+(operation.equals("create")?"create":operation.equals("delete")?"delete":"update");
        return ProjectOwnerOperationScope.call("SOL","SITE_SURVEY",()->new ProjectOwnerOperationScope.Declaration(
                actor.tenantId(),actor.userId(),row.getProjectId(),"SOL","SITE_SURVEY",nativeOperation,1,
                current==null?null:current.getId().toString(),null),()->{
            access.lock(row.getProjectId(),permission,null,nativeOperation,current==null?null:current.getId());return work.get();
        });
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
    @Override protected void afterChange(String operation,SiteSurveyEntityDO before,SiteSurveyEntityDO row){changed(row);}
    @Override protected void validateDelivery(SiteSurveyEntityDO row,boolean write){if(write)requireState(row,0);}
    public BusinessOperationReceipt confirm(Long id,Long version,String key){return change("confirm",id,version,key,Map.of(),row->{requireState(row,0);forms.validate(row,false);row.setStatus(1);row.setConfirmedAt(LocalDateTime.now());});}
    public BusinessOperationReceipt reject(Long id,Long version,String key){return change("reject",id,version,key,Map.of(),row->{requireState(row,0);row.setStatus(2);});}
    public BusinessOperationReceipt archive(Long id,Long version,String key){return change("archive",id,version,key,Map.of(),row->{requireState(row,1);row.setStatus(3);row.setArchivedAt(LocalDateTime.now());});}
    private void changed(SiteSurveyEntityDO row){rules.changed(row.getProjectId(),"SiteSurvey",row.getId(),actor().userId(),"site-survey:"+row.getId()+":"+row.getVersion());}
    private void requireState(SiteSurveyEntityDO row,int state){if(!Objects.equals(row.getStatus(),state))throw exception(SITE_SURVEY_STATUS_INVALID);}
    private void requireLocation(SiteSurveyEntityDO row){if(row.getLocation()==null||row.getLocation().isBlank())throw exception(SITE_SURVEY_LOCATION_REQUIRED);}
}
