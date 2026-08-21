package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTemplateCandidateWatermarkTest {

    @Mock
    private ProjectTemplateMapper templateMapper;
    @Mock
    private ProjectTemplateRevisionMapper revisionMapper;
    @InjectMocks
    private ProjectTemplateServiceImpl service;

    @Test
    void watermarkIsStableAndChangesWithPublishedRevision() {
        ProjectTemplateDO template = new ProjectTemplateDO();
        template.setId(9L);
        template.setCode("TPL-09");
        template.setName("标准模板");
        template.setStatus(TemplateRules.STATUS_ACTIVE);
        template.setMatchPriority(10);
        when(templateMapper.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE))
                .thenReturn(List.of(template));

        when(revisionMapper.selectPublishedListByTemplateId(9L))
                .thenReturn(List.of(revision(9001L, 1)));
        var first = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);
        var repeated = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);

        assertNotNull(first.getCandidateWatermark());
        assertEquals(first.getCandidateWatermark(), repeated.getCandidateWatermark());
        assertEquals(9001L, first.getMatched().getTemplateRevisionId());

        when(revisionMapper.selectPublishedListByTemplateId(9L))
                .thenReturn(List.of(revision(9002L, 2)));
        var changed = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);

        assertNotEquals(first.getCandidateWatermark(), changed.getCandidateWatermark());
        assertEquals(9002L, changed.getMatched().getTemplateRevisionId());
    }

    private ProjectTemplateRevisionDO revision(Long id, Integer revisionNo) {
        ProjectTemplateRevisionDO revision = new ProjectTemplateRevisionDO();
        revision.setId(id);
        revision.setTemplateId(9L);
        revision.setRevisionNo(revisionNo);
        revision.setStatus(TemplateRules.REVISION_STATUS_PUBLISHED);
        revision.setSigningMethod("DIRECT");
        revision.setProjectCategory("GENERAL");
        revision.setImplementationMethod("ONSITE");
        return revision;
    }
}
