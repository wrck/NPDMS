package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.pms.project.domain.template.RequirementAnalysisWorkBindingSchema;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequirementAnalysisDictionarySnapshotTest {

    @Test
    void validatesRequestedCodesAndReturnsOnlyEnabledAuthoritativeLabels() {
        DictDataApi dictDataApi = mock(DictDataApi.class);
        ProjectTemplateServiceImpl service = new ProjectTemplateServiceImpl();
        ReflectionTestUtils.setField(service, "dictDataApi", dictDataApi);
        when(dictDataApi.getDictDataList("pms_requirement_level")).thenReturn(List.of(
                dict("pms_requirement_level", "A", "甲", CommonStatusEnum.ENABLE.getStatus()),
                dict("pms_requirement_level", "B", "乙", CommonStatusEnum.ENABLE.getStatus()),
                dict("pms_requirement_level", "C", "丙", CommonStatusEnum.DISABLE.getStatus())));

        var requested = List.of(
                new RequirementAnalysisWorkBindingSchema.OptionSnapshot("B", "旧乙"),
                new RequirementAnalysisWorkBindingSchema.OptionSnapshot("A", "旧甲"));
        var resolved = service.resolveEnabledDictionaryOptions("pms_requirement_level", requested);

        verify(dictDataApi).validateDictDataList("pms_requirement_level", Set.of("A", "B"));
        assertEquals(Set.of(
                new RequirementAnalysisWorkBindingSchema.OptionSnapshot("A", "甲"),
                new RequirementAnalysisWorkBindingSchema.OptionSnapshot("B", "乙")), Set.copyOf(resolved));
    }

    @Test
    void missingOrDisabledRequestedCodeFailsClosed() {
        DictDataApi dictDataApi = mock(DictDataApi.class);
        ProjectTemplateServiceImpl service = new ProjectTemplateServiceImpl();
        ReflectionTestUtils.setField(service, "dictDataApi", dictDataApi);
        when(dictDataApi.getDictDataList("pms_requirement_level")).thenReturn(List.of(
                dict("pms_requirement_level", "A", "甲", CommonStatusEnum.DISABLE.getStatus())));

        assertThrows(IllegalArgumentException.class, () -> service.resolveEnabledDictionaryOptions(
                "pms_requirement_level",
                List.of(new RequirementAnalysisWorkBindingSchema.OptionSnapshot("A", "甲"))));
    }

    private static DictDataRespDTO dict(String type, String code, String label, Integer status) {
        DictDataRespDTO value = new DictDataRespDTO();
        value.setDictType(type);
        value.setValue(code);
        value.setLabel(label);
        value.setStatus(status);
        return value;
    }
}
