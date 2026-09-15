package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchFacts;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private ProjectTemplateV2ServiceImpl service;
    private static RuleEngineTestFixture engine;
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        service = new ProjectTemplateV2ServiceImpl();
        ReflectionTestUtils.setField(service, "v2TemplateMapper", templateMapper);
        ReflectionTestUtils.setField(service, "v2RevisionMapper", revisionMapper);
        ReflectionTestUtils.setField(service, "matchRuleEvaluator", new ProjectTemplateMatchRuleEvaluator(
                new ProjectRuleCompiler(), engine.evaluator(), org.mockito.Mockito.mock(ProjectDecisionTableService.class)));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

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
        var first = service.matchPreview(new TemplateMatchFacts(Map.of()));
        var repeated = service.matchPreview(new TemplateMatchFacts(Map.of()));

        assertNotNull(first.getCandidateWatermark());
        assertEquals(first.getCandidateWatermark(), repeated.getCandidateWatermark());
        assertEquals(9001L, first.getMatched().getTemplateRevisionId());

        when(revisionMapper.selectPublishedListByTemplateId(9L))
                .thenReturn(List.of(revision(9002L, 2)));
        var changed = service.matchPreview(new TemplateMatchFacts(Map.of()));

        assertNotEquals(first.getCandidateWatermark(), changed.getCandidateWatermark());
        assertEquals(9002L, changed.getMatched().getTemplateRevisionId());
    }

    @Test void fullFactsChangeWatermarkEvenWithSameOutcomeButOrderDoesNot() {
        when(templateMapper.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE)).thenReturn(List.of());
        var facts = new LinkedHashMap<String, RuleFact>();
        facts.put("project.projectName", RuleFact.known("现场工勘"));
        facts.put("project.isChild", RuleFact.known(true));
        var first = service.matchPreview(new TemplateMatchFacts(facts));
        var reordered = new LinkedHashMap<String, RuleFact>();
        reordered.put("project.isChild", RuleFact.known(true));
        reordered.put("project.projectName", RuleFact.known("现场工勘"));
        assertEquals(first.getCandidateWatermark(), service.matchPreview(new TemplateMatchFacts(reordered)).getCandidateWatermark());
        facts.put("project.projectName", RuleFact.known("需求分析"));
        assertNotEquals(first.getCandidateWatermark(), service.matchPreview(new TemplateMatchFacts(facts)).getCandidateWatermark());
        var missing = service.matchPreview(new TemplateMatchFacts(Map.of()));
        var empty = service.matchPreview(new TemplateMatchFacts(Map.of("project.customerCode", RuleFact.known(null))));
        assertNotEquals(missing.getCandidateWatermark(), empty.getCandidateWatermark());
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
        var snapshot = new TemplateExecutionSnapshot();
        snapshot.setCompilerVersion("template-compiler-v2-test");
        revision.setExecutionSchemaVersion(TemplateExecutionSnapshot.SCHEMA_VERSION);
        revision.setCompilerVersion(snapshot.getCompilerVersion());
        revision.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        revision.setSnapshotHash(TemplateExecutionSnapshotHasher.hash(snapshot));
        return revision;
    }
}
