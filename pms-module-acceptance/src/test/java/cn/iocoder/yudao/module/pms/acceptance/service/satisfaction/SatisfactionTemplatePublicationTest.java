package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SatisfactionTemplatePublicationTest {
    private final SatisfactionQuestionnaireTemplateMapper roots = mock(SatisfactionQuestionnaireTemplateMapper.class);
    private final SatisfactionQuestionnaireTemplateRevisionMapper revisions = mock(SatisfactionQuestionnaireTemplateRevisionMapper.class);
    private final SatisfactionTemplateManagementService service = new SatisfactionTemplateManagementService(roots, revisions, mock(PlatformCommandExecutionApi.class));

    private SatisfactionQuestionnaireTemplateRevisionDO setup(Long otherTemplateId) {
        var root = new SatisfactionQuestionnaireTemplateDO();
        root.setId(10L); root.setCurrentRevisionId(20L); root.setVersion(1L);
        when(roots.selectByIdForUpdate(1L, 10L)).thenReturn(root);
        var draft = new SatisfactionQuestionnaireTemplateRevisionDO();
        draft.setId(21L); draft.setTemplateId(10L); draft.setRevisionStatus("DRAFT"); draft.setVersion(0); draft.setRevisionNo(2);
        draft.setRuleVersion("R1"); draft.setFrozenThreshold(new BigDecimal("80.00"));
        draft.setFrozenQuestionJson("""
            {"schemaVersion":1,"questions":[{"code":"Q1","title":"评价","type":"RATING","required":true,
            "options":[{"code":"A","label":"满意","score":"100.00"}]}],
            "scoring":{"ruleVersion":"R1","strategy":"SUM_V1","scoreMin":"0.00","scoreMax":"100.00",
            "threshold":"80.00","precision":2,"roundingMode":"HALF_UP"}}
            """);
        when(revisions.selectByIdForUpdate(1L, 21L)).thenReturn(draft);
        var previous = new SatisfactionQuestionnaireTemplateRevisionDO();
        previous.setId(20L); previous.setTemplateId(otherTemplateId); previous.setRevisionStatus("PUBLISHED"); previous.setVersion(1);
        when(revisions.selectByIdForUpdate(1L, 20L)).thenReturn(previous);
        when(revisions.selectPublishedByApplicability(any())).thenReturn(List.of(previous));
        return previous;
    }
    @Test void supersedesOnlyTheCurrentRevisionOfTheSameTemplate() {
        var previous = setup(10L);
        var result = service.publishOnce(1L, 1L, 10L, 21L, 0);
        assertEquals(21L, result.revisionId());
        assertEquals("SUPERSEDED", previous.getRevisionStatus());
        assertNotNull(previous.getEffectiveTo());
        verify(revisions).updateById(previous);
    }
    @Test void keepsOtherTemplateAmbiguityProtection() {
        var previous = setup(11L);
        assertThrows(IllegalStateException.class, () -> service.publishOnce(1L, 1L, 10L, 21L, 0));
        assertEquals("PUBLISHED", previous.getRevisionStatus());
        verify(revisions, never()).updateById(any(SatisfactionQuestionnaireTemplateRevisionDO.class));
    }
}
