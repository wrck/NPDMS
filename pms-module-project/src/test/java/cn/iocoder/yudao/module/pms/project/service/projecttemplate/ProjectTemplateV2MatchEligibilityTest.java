package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTemplateV2MatchEligibilityTest {

    @Test
    void legacyPublishedRevisionIsNotAdvertisedAsNewProjectCandidate() {
        ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
        ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
        ProjectTemplateV2ServiceImpl service = service(templates, revisions);

        ProjectTemplateDO legacy = template(1L, "LEGACY");
        ProjectTemplateDO v2 = template(2L, "V2");
        when(templates.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE))
                .thenReturn(List.of(legacy, v2));
        when(revisions.selectPublishedListByTemplateId(1L)).thenReturn(List.of(revision(11L, 1, false)));
        when(revisions.selectPublishedListByTemplateId(2L)).thenReturn(List.of(revision(22L, 2, true)));

        TemplateMatchResult result = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);

        assertEquals(TemplateMatchResult.Outcome.MATCHED, result.getOutcome());
        assertNotNull(result.getMatched());
        assertEquals(2L, result.getMatched().getTemplateId());
        assertEquals(22L, result.getMatched().getTemplateRevisionId());
        assertEquals(1, result.getCandidates().size());
        assertNotNull(result.getCandidateWatermark());
    }

    @Test
    void activeTemplateWithOnlyLegacyPublishedRevisionProducesNoMatch() {
        ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
        ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
        ProjectTemplateV2ServiceImpl service = service(templates, revisions);

        ProjectTemplateDO legacy = template(1L, "LEGACY");
        when(templates.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE)).thenReturn(List.of(legacy));
        when(revisions.selectPublishedListByTemplateId(1L)).thenReturn(List.of(revision(11L, 1, false)));

        TemplateMatchResult result = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);

        assertEquals(TemplateMatchResult.Outcome.NO_MATCH, result.getOutcome());
        assertTrue(result.getCandidates().isEmpty());
        assertNotNull(result.getCandidateWatermark());
    }

    private ProjectTemplateV2ServiceImpl service(ProjectTemplateMapper templates,
                                                 ProjectTemplateRevisionMapper revisions) {
        ProjectTemplateV2ServiceImpl service = new ProjectTemplateV2ServiceImpl();
        ReflectionTestUtils.setField(service, "v2TemplateMapper", templates);
        ReflectionTestUtils.setField(service, "v2RevisionMapper", revisions);
        return service;
    }

    private ProjectTemplateDO template(Long id, String code) {
        ProjectTemplateDO template = new ProjectTemplateDO();
        template.setId(id);
        template.setCode(code);
        template.setName(code);
        template.setStatus(TemplateRules.STATUS_ACTIVE);
        template.setMatchPriority(100);
        return template;
    }

    private ProjectTemplateRevisionDO revision(Long id, int revisionNo, boolean v2) {
        ProjectTemplateRevisionDO revision = new ProjectTemplateRevisionDO();
        revision.setId(id);
        revision.setRevisionNo(revisionNo);
        revision.setStatus(TemplateRules.REVISION_STATUS_PUBLISHED);
        revision.setSigningMethod("DIRECT");
        revision.setProjectCategory("GENERAL");
        revision.setImplementationMethod("ONSITE");
        if (v2) {
            revision.setExecutionSchemaVersion(TemplateExecutionSnapshot.SCHEMA_VERSION);
            revision.setExecutionSnapshot("{}");
            revision.setCompilerVersion("template-compiler-v2-test");
            revision.setSnapshotHash("snapshot-hash");
        }
        return revision;
    }
}
