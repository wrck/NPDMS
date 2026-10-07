package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Native and controlled entry protocols converge on the same public request and receipt. */
@Service
@RequiredArgsConstructor
public class SiteSurveyEntityCommands {
    private final BusinessOperationDispatcher dispatcher;
    private final BusinessCallerContext callerContext;
    @SuppressWarnings("unchecked")
    public BusinessOperationReceipt executeReceipt(String code,Long id,Long version,SiteSurveyEntitySaveReqVO save,
            ProjectBusinessExecutionSelection selection,String key,OperationEntryKind entryKind) {
        var caller=callerContext.require();
        Map<String,Object> input=new LinkedHashMap<>();
        if (save!=null) {
            Map<String,Object> values=JsonUtils.parseObject(JsonUtils.toJsonString(save),Map.class);
            // The trusted target/version and selection have a single representation in the common request.
            for(String field:List.of("id","version","status","execution")) values.remove(field);
            input.put("values",values);
        }
        if (selection!=null) input.put("execution",selection);
        EntityDataRef target=id==null ? null : EntityDataRef.current(new EntityRef(caller.tenantId(),"SOL","siteSurvey",id));
        return dispatcher.dispatch(new BusinessOperationRequest(code,1,target,"SOL","siteSurvey",input,key,version,
                entryKind,caller.entryCorrelationId()));
    }
}
