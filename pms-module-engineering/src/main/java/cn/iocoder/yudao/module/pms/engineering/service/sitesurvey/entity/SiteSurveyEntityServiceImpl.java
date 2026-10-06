package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyOperationIdentityQuery;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.util.UUID;

/** Existing HTTP response shapes remain compatible; six user commands use the public execution chain. */
@Service
@Validated
@RequiredArgsConstructor
public class SiteSurveyEntityServiceImpl implements SiteSurveyEntityService {
    private final SiteSurveyEntityCommands commands;
    private final SiteSurveyEntityDomainCommands domain;
    private final SiteSurveyEntityMapper mapper;
    @Override public Long createSiteSurveyEntity(SiteSurveyEntitySaveReqVO request) {
        var receipt=commands.executeReceipt("create",null,null,request,request.getExecution(),key(),OperationEntryKind.INDEPENDENT);
        return receipt.entityRef().entityId();
    }
    @Override public void updateSiteSurveyEntity(SiteSurveyEntitySaveReqVO request) {
        commands.executeReceipt("save",request.getId(),request.getVersion(),request,request.getExecution(),key(),OperationEntryKind.INDEPENDENT);
    }
    @Override public void deleteSiteSurveyEntity(Long id,ProjectBusinessExecutionSelection selection) { action("delete",id,selection); }
    @Override public void confirmSiteSurveyEntity(Long id,ProjectBusinessExecutionSelection selection) { action("confirm",id,selection); }
    @Override public void rejectSiteSurveyEntity(Long id,ProjectBusinessExecutionSelection selection) { action("reject",id,selection); }
    @Override public void archiveSiteSurveyEntity(Long id,ProjectBusinessExecutionSelection selection) { action("archive",id,selection); }
    private void action(String code,Long id,ProjectBusinessExecutionSelection selection) {
        String expected=header("X-Expected-Version");
        Long version;
        if (expected!=null) {
            try { version=Long.valueOf(expected); } catch(NumberFormatException invalid) { throw invalid("并发依据无效"); }
        } else {
            if (header("Idempotency-Key")!=null) throw invalid("幂等动作须携带 X-Expected-Version");
            var row=mapper.selectOperationIdentity(new SiteSurveyOperationIdentityQuery(TenantContextHolder.getRequiredTenantId(),id));
            if (row==null) throw invalid("工勘不存在");
            version=row.getVersion();
        }
        commands.executeReceipt(code,id,version,null,selection,key(),OperationEntryKind.INDEPENDENT);
    }
    private String key() {String key=header("Idempotency-Key");return key==null ? UUID.randomUUID().toString() : key;}
    private String header(String name) {var request=ServletUtils.getRequest();if(request==null)return null;String value=request.getHeader(name);return value==null || value.isBlank() ? null : value;}
    private static BusinessContractException invalid(String message) {return new BusinessContractException("OPERATION_INPUT_INVALID",message);}
    @Override public SiteSurveyEntityDO getSiteSurveyEntity(Long id) {return domain.getSiteSurveyEntity(id);}
    @Override public PageResult<SiteSurveyEntityDO> getSiteSurveyEntityPage(SiteSurveyEntityPageReqVO request) {return domain.getSiteSurveyEntityPage(request);}
    // Cross-aggregate outsourcing callbacks retain their original domain boundary until that aggregate migrates.
    @Override public void associateOutsourceRequest(Long id,Long project,Long outsource,ProjectBusinessExecutionSelection selection) {
        domain.associateOutsourceRequest(id,project,outsource,selection);
    }
    @Override public void releaseDeletedOutsourceRequest(Long id,Long outsource,ProjectBusinessExecutionSelection selection) {
        domain.releaseDeletedOutsourceRequest(id,outsource,selection);
    }
}
