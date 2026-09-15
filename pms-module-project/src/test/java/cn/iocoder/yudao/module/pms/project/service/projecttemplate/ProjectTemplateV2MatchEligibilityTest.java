package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.ProjectAttributeResolutionService;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectTemplateV2MatchEligibilityTest {
    private static RuleEngineTestFixture engine;
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { if (engine != null) engine.close(); }
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

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

    @Test
    void corruptV2SnapshotIsNotAdvertisedAsNewProjectCandidate() {
        ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
        ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
        ProjectTemplateV2ServiceImpl service = service(templates, revisions);

        ProjectTemplateDO v2 = template(2L, "V2");
        ProjectTemplateRevisionDO corrupt = revision(22L, 2, true);
        corrupt.setSnapshotHash("tampered");
        when(templates.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE)).thenReturn(List.of(v2));
        when(revisions.selectPublishedListByTemplateId(2L)).thenReturn(List.of(corrupt));

        TemplateMatchResult result = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);

        assertEquals(TemplateMatchResult.Outcome.NO_MATCH, result.getOutcome());
        assertTrue(result.getCandidates().isEmpty());
    }

    @Test
    void frozenRuleOverridesOldColumnsAndCreationReusesPreviewEvaluation() {
        var templates = mock(ProjectTemplateMapper.class);
        var revisions = mock(ProjectTemplateRevisionMapper.class);
        var service = service(templates, revisions);
        var latest = ruleRevision(22L, 2, """
                {"operator":"ANY","rules":[
                  {"predicate":"FIELD","parameters":{"fieldCode":"project.signingMethod","valueType":"TEXT","operator":"=","value":"DIRECT_SIGN"}},
                  {"predicate":"FIELD","parameters":{"fieldCode":"project.projectCategory","valueType":"TEXT","operator":"=","value":"ENGINEERING"}}]}
                """);
        latest.setSigningMethod("DOES_NOT_MATCH");
        when(templates.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE)).thenReturn(List.of(template(2L, "V2")));
        when(revisions.selectPublishedListByTemplateId(2L)).thenReturn(List.of(latest));
        var preview = service.matchPreview("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null);
        assertEquals(22L, preview.getMatched().getTemplateRevisionId());
        assertEquals("适用条件", preview.getMatched().getRuleName());
        assertEquals(RuleEvaluation.Outcome.MATCHED, preview.getEvaluations().getFirst().result().outcome());
        var creation = new ProjectAttributeResolutionService();
        ReflectionTestUtils.setField(creation, "projectTemplateService", service);
        var decision = creation.resolveInitial(new ProjectAttributeSnapshot("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null),
                22L, preview.getCandidateWatermark());
        assertEquals(22L, decision.matchedTemplateRevisionId());
        assertEquals(preview.getCandidateWatermark(), decision.candidateDigest());
        var controller = new cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.ProjectTemplateController();
        ReflectionTestUtils.setField(controller, "projectTemplateService", service);
        var request = new cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateMatchPreviewReqVO();
        request.setSigningMethod("DIRECT_SIGN"); request.setProjectCategory("GENERAL"); request.setImplementationMethod("DIRECT_SERVICE");
        var response = controller.matchPreview(request).getData();
        assertEquals(preview.getEvaluations(), response.getEvaluations());
        var json = JsonUtils.parseObject(JsonUtils.toJsonString(response), tools.jackson.databind.JsonNode.class);
        assertEquals("CONDITION", json.path("evaluations").get(0).path("result").path("kind").asText());
        org.junit.jupiter.api.Assertions.assertFalse(json.toString().contains("DIRECT_SIGN"));
        var projects = new cn.iocoder.yudao.module.pms.project.controller.admin.projects.ProjectMasterController();
        ReflectionTestUtils.setField(projects, "projectTemplateService", service);
        var creationResponse = projects.matchTemplates("DIRECT_SIGN", "GENERAL", "DIRECT_SERVICE", null).getData();
        assertEquals(preview.getEvaluations(), creationResponse.getEvaluations());
        assertEquals("适用条件", creationResponse.getCandidates().getFirst().getRuleName());
    }

    @Test
    void unknownCandidateCannotWinAndIndependentCandidateStillMatches() {
        var templates = mock(ProjectTemplateMapper.class);
        var revisions = mock(ProjectTemplateRevisionMapper.class);
        var service = service(templates, revisions);
        var unknown = template(1L, "MISSING_FACT"); unknown.setMatchPriority(1);
        when(templates.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE)).thenReturn(List.of(unknown, template(2L, "INDEPENDENT")));
        when(revisions.selectPublishedListByTemplateId(1L)).thenReturn(List.of(ruleRevision(11L, 1, """
                {"operator":"NOT","rules":[{"predicate":"FIELD","parameters":{
                "fieldCode":"project.businessType","valueType":"TEXT","operator":"=","value":"DELIVERY"}}]}
                """)));
        when(revisions.selectPublishedListByTemplateId(2L)).thenReturn(List.of(revision(22L, 2, true)));
        var result = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);
        assertEquals(22L, result.getMatched().getTemplateRevisionId());
        assertEquals(2, result.getEvaluations().size());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.getEvaluations().getFirst().result().outcome());
        assertEquals("MATCH_FIELD_UNAVAILABLE", result.getEvaluations().getFirst().result().conditions().getFirst().reasonCode());
    }

    @Test
    void falseIsNotSuccessfulMatchingAndEqualPriorityRemainsExplicit() {
        var templates = mock(ProjectTemplateMapper.class);
        var revisions = mock(ProjectTemplateRevisionMapper.class);
        var service = service(templates, revisions);
        var rejected = template(1L, "FALSE"); rejected.setMatchPriority(1);
        when(templates.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE))
                .thenReturn(List.of(rejected, template(2L, "A"), template(3L, "B")));
        when(revisions.selectPublishedListByTemplateId(1L)).thenReturn(List.of(ruleRevision(11L, 1,
                "{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}")));
        when(revisions.selectPublishedListByTemplateId(2L)).thenReturn(List.of(revision(22L, 2, true)));
        when(revisions.selectPublishedListByTemplateId(3L)).thenReturn(List.of(revision(33L, 3, true)));
        var result = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);
        assertEquals(TemplateMatchResult.Outcome.MULTI_MATCH, result.getOutcome());
        assertEquals(List.of(22L, 33L), result.getCandidates().stream().map(candidate -> candidate.getTemplateRevisionId()).toList());
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, result.getEvaluations().getFirst().result().outcome());
    }

    @Test
    void publishedRuleVersionChangeInvalidatesOldCandidateWatermark() {
        var templates = mock(ProjectTemplateMapper.class);
        var revisions = mock(ProjectTemplateRevisionMapper.class);
        var service = service(templates, revisions);
        when(templates.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE)).thenReturn(List.of(template(2L, "V2")));
        when(revisions.selectPublishedListByTemplateId(2L)).thenReturn(List.of(revision(22L, 2, true)));
        var before = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);
        when(revisions.selectPublishedListByTemplateId(2L)).thenReturn(List.of(ruleRevision(23L, 3,
                "{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}")));
        var after = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);
        assertEquals(TemplateMatchResult.Outcome.NO_MATCH, after.getOutcome());
        org.junit.jupiter.api.Assertions.assertNotEquals(before.getCandidateWatermark(), after.getCandidateWatermark());
        var creation = new ProjectAttributeResolutionService();
        ReflectionTestUtils.setField(creation, "projectTemplateService", service);
        org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> creation.resolveInitial(new ProjectAttributeSnapshot("DIRECT", "GENERAL", "ONSITE", null), 22L, before.getCandidateWatermark()));
    }

    @Test
    void absentNullableInputCannotBeNegatedIntoEligibility() {
        var templates = mock(ProjectTemplateMapper.class);
        var revisions = mock(ProjectTemplateRevisionMapper.class);
        var service = service(templates, revisions);
        when(templates.selectListByStatusOrderByPriority(TemplateRules.STATUS_ACTIVE)).thenReturn(List.of(template(2L, "V2")));
        var latest = ruleRevision(22L, 2, """
                {"operator":"NOT","rules":[{"predicate":"FIELD","parameters":{
                "fieldCode":"project.majorProjectLevel","valueType":"TEXT","operator":"=","value":"MAJOR"}}]}
                """);
        when(revisions.selectPublishedListByTemplateId(2L)).thenReturn(List.of(latest));
        var result = service.matchPreview("DIRECT", "GENERAL", "ONSITE", null);
        assertEquals(TemplateMatchResult.Outcome.NO_MATCH, result.getOutcome());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.getEvaluations().getFirst().result().outcome());
    }

    private ProjectTemplateRevisionDO ruleRevision(Long id, int number, String expression) {
        var revision = revision(id, number, true);
        var snapshot = JsonUtils.parseObject(revision.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        var json = JsonUtils.parseObject(expression, tools.jackson.databind.JsonNode.class);
        snapshot.setMatchRuleKey("applicable");
        snapshot.setRules(List.of(new VersionRule("applicable", "适用条件", VersionRule.Kind.CONDITION, false, json, null)));
        snapshot.setRulePrograms(Map.of("applicable", new ProjectRuleCompiler().compile(json)));
        revision.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        revision.setSnapshotHash(TemplateExecutionSnapshotHasher.hash(snapshot));
        return revision;
    }

    private ProjectTemplateV2ServiceImpl service(ProjectTemplateMapper templates,
                                                  ProjectTemplateRevisionMapper revisions) {
        ProjectTemplateV2ServiceImpl service = new ProjectTemplateV2ServiceImpl();
        ReflectionTestUtils.setField(service, "v2TemplateMapper", templates);
        ReflectionTestUtils.setField(service, "v2RevisionMapper", revisions);
        ReflectionTestUtils.setField(service, "matchRuleEvaluator", new ProjectTemplateMatchRuleEvaluator(
                new ProjectRuleCompiler(), engine.evaluator(), mock(ProjectDecisionTableService.class)));
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
            TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
            snapshot.setCompilerVersion("template-compiler-v2-test");
            revision.setExecutionSchemaVersion(TemplateExecutionSnapshot.SCHEMA_VERSION);
            revision.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
            revision.setCompilerVersion(snapshot.getCompilerVersion());
            revision.setSnapshotHash(TemplateExecutionSnapshotHasher.hash(snapshot));
        }
        return revision;
    }
}
