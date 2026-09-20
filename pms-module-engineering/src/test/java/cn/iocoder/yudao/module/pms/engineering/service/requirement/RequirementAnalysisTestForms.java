package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import java.util.List;
import static org.mockito.Mockito.*;

final class RequirementAnalysisTestForms {
    static DynamicFormBusinessInstanceApi published() {
        var api = mock(DynamicFormBusinessInstanceApi.class);
        var revision = mock(DynamicFormRevisionFact.class);
        when(revision.fields()).thenReturn(RequirementAnalysisEntityProvider.FIELDS.fields().stream()
                .map(field -> new DynamicFormFieldDescriptor(RequirementAnalysisFields.formKey(field.code()),
                        "input", false, field.required(), "any", null, null, null, List.of())).toList());
        when(api.inspectRevisionForUsage(any())).thenReturn(revision);
        return api;
    }
}
