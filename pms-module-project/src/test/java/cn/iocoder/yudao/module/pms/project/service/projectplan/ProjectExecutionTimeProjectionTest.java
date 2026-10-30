package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultEvidenceItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult.ResultEvidenceScanDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.businessresult.ResultEvidenceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.completion.ProjectAssignmentCompletionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectBusinessResultSources;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectExecutionTimeProjectionTest {
    final ResultEvidenceMapper evidence = mock(ResultEvidenceMapper.class);
    final ProjectAssignmentCompletionMapper members = mock(ProjectAssignmentCompletionMapper.class);
    final ProjectBusinessResultSources sources = mock(ProjectBusinessResultSources.class);
    final TaskBusinessObjectProvider owner = mock(TaskBusinessObjectProvider.class);
    final ProjectExecutionTimeProjection service = new ProjectExecutionTimeProjection(evidence, members, sources, List.of(owner));
    final LocalDateTime finished = LocalDateTime.of(2026,10,22,13,0);
    ProjectNodeExecutionDO round() {
        var r = new ProjectNodeExecutionDO(); r.setId(31L); r.setTenantId(7L); r.setProjectId(9L); r.setPlanVersionId(51L);
        r.setNodeKind("TASK"); r.setNodeKey("task:one"); r.setNodeInstanceId(11L); r.setStatus("DONE"); r.setRoundNo(1);
        r.setEndedAt(finished); r.setResultSnapshot("{}"); return r;
    }
    ProjectExecutionTimeProjection.Query query(ProjectNodeExecutionDO r) {
        var snapshot = new TemplateExecutionSnapshot();
        var task = new TemplateExecutionSnapshot.TaskContract(); task.setNodeKey("task:one"); task.setCompletionRuleKey("complete"); snapshot.setTasks(List.of(task));
        snapshot.getRulePrograms().put("complete", new cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram(
                cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule.Kind.CONDITION,"",List.of(new cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram.Leaf(
                "leaf","rule","BUSINESS_FACT",JsonUtils.parseTree("{\"factCode\":\"TRAINING_CONFIRMED\"}")))));
        return new ProjectExecutionTimeProjection.Query(7L,9L,1L,r,snapshot,List.of(r));
    }
    ProjectMemberAssignmentDO member(Long id, String role, LocalDateTime at) {
        var row = new ProjectMemberAssignmentDO(); row.setId(id); row.setTenantId(7L); row.setProjectId(9L);
        row.setMemberRole(role); row.setEffectiveFrom(at); return row;
    }
    @Test void manualSubmissionWinsAndDoesNotReadBusinessState() {
        var r=round(); r.setSubmittedAt(finished.minusMinutes(5));
        assertEquals(r.getSubmittedAt(),service.project(query(r)).at());
        assertEquals("MANUAL",service.project(query(r)).source());
        verifyNoInteractions(evidence,members,sources,owner);
    }
    @Test void incompleteOrForeignRoundCannotInventSubmissionTime() {
        var r=round(); r.setStatus("ACTIVE"); assertNull(service.project(query(r)));
        r.setStatus("DONE"); r.setTenantId(8L); assertNull(service.project(query(r)));
        verifyNoInteractions(evidence,members,sources,owner);
    }
    @Test void endUserUsesFrozenIntervalIdsAndFirstJoiningTimeRatherThanCurrentMembers() {
        var r=round(); r.setResultSnapshot("{\"projectEndUsers\":{\"projectId\":9,\"members\":[{\"assignmentId\":61},{\"assignmentId\":62}]}}");
        when(members.members(any())).thenReturn(List.of(member(61L,"END_USER",finished.minusHours(2)),member(62L,"END_USER",finished.minusHours(1)),member(63L,"END_USER",finished.minusDays(7))));
        assertEquals(finished.minusHours(2),service.project(query(r)).at());
        assertNull(r.getSubmittedAt());
        verify(members).members(argThat(q -> q.tenantId().equals(7L) && q.projectId().equals(9L) && !q.lock()));
    }
    @Test void missingFrozenIntervalDoesNotFallBackToTodaysReplacement() {
        var r=round(); r.setResultSnapshot("{\"projectEndUsers\":{\"projectId\":9,\"members\":[{\"assignmentId\":61}]}}");
        when(members.members(any())).thenReturn(List.of(member(62L,"END_USER",finished.minusMinutes(1))));
        assertNull(service.project(query(r)));
    }
    @Test void frozenResultFormationTimeSurvivesLaterBusinessChanges() {
        var r=round(); r.setResultSnapshot("{\"subscriptionEvidence\":[{\"executionId\":31,\"planVersionId\":51,\"scanId\":71}]}");
        var item=new ResultEvidenceItemDO(); item.setId(81L); item.setTenantId(7L); item.setProjectId(9L); item.setScanId(71L);
        item.setObjectId("91"); item.setResultId("101"); item.setEligibility("ELIGIBLE");
        item.setObservation(JsonUtils.toJsonString(BusinessResultSource.Observation.available(new BusinessResultSource.Result(7L,9L,
                new BusinessResultSource.Type("SOL","REQUIREMENT_ANALYSIS","REQUIREMENT_ANALYSIS_COMPLETED"),"91","101","1","2",BusinessResultSource.Validity.CURRENT,finished.minusHours(3)))));
        var scan = new ResultEvidenceScanDO(); scan.setTenantId(7L); scan.setProjectId(9L); scan.setId(71L);
        scan.setSubscriptionId(0L); scan.setSubscriptionVersion(0); scan.setThroughSequence(0L); scan.setStatus("SATISFIED");
        when(evidence.selectById(any())).thenReturn(scan);
        when(evidence.selectItems(any())).thenReturn(List.of(item));
        assertEquals(finished.minusHours(3),service.project(query(r)).at());
        verifyNoInteractions(sources,owner);
        item.setProjectId(99L); assertNull(service.project(query(r)));
    }
    @Test void ownerExactFactTimeIsReadOnlyAndNeverUsesSystemCompletionTime() {
        var r=round(); r.setResultSnapshot("{\"ownerContext\":\"IMP\",\"objectType\":\"TRAINING\",\"businessFacts\":{\"results\":[{\"objectId\":\"91\",\"factVersion\":\"v2:execution:31\",\"facts\":{\"TRAINING_CONFIRMED\":true}}]}}");
        when(owner.ownerContext()).thenReturn("IMP"); when(owner.objectType()).thenReturn("TRAINING");
        when(owner.historicalHandlingTime(any())).thenReturn(new TaskBusinessObjectProvider.HandlingTime(finished.minusHours(1),"BUSINESS_END","客户确认时间"));
        assertEquals(finished.minusHours(1),service.project(query(r)).at());
        verify(owner).historicalHandlingTime(argThat(q -> q.factVersion().equals("v2") && q.context().tenantId().equals(7L) && q.satisfiedFacts().equals(Set.of("TRAINING_CONFIRMED"))));
        verify(owner,never()).lockAndRevalidate(any(),any(),any());
        when(owner.historicalHandlingTime(any())).thenReturn(new TaskBusinessObjectProvider.HandlingTime(finished.plusDays(1),"BUSINESS_END","客户确认时间"));
        assertNull(service.project(query(r)));
    }
    @Test void assignmentUsesFrozenRoleIntervalsRatherThanUnrelatedMembers() {
        var r=round();
        r.setResultSnapshot("{\"ownerContext\":\"PROJ\",\"objectType\":\"PROJECT_ASSIGNMENT\",\"businessFacts\":{\"results\":[{\"factVersion\":\"PROJECT_ASSIGNMENT:v1:4:61:1,62:1,63:1:true:execution:31\",\"facts\":{\"PROJECT_ASSIGNMENT_COMPLETED\":true}}]}}");
        when(members.members(any())).thenReturn(List.of(member(61L,"SERVICE_MANAGER",finished.minusHours(2)),member(62L,"PROJECT_MANAGER",finished.minusHours(1)),member(63L,"END_USER",finished.minusMinutes(1))));
        var time=service.project(query(r));
        assertEquals(finished.minusHours(1),time.at()); assertEquals("BUSINESS_START",time.source());
        assertNull(r.getSubmittedAt());
        r.setNodeKind("STAGE");
        r.setResultSnapshot(r.getResultSnapshot().replace("\"ownerContext\":\"PROJ\",\"objectType\":\"PROJECT_ASSIGNMENT\",", "\"businessResults\":[{\"ownerContext\":\"PROJ\",\"objectType\":\"PROJECT_ASSIGNMENT\"}],"));
        assertEquals(time.at(),service.project(query(r)).at());
    }
    @Test void stageUsesBusinessTimesOfRequiredTasksAndKeepsRoundEndTimeIntact() {
        var stage=round(); stage.setNodeKind("STAGE"); stage.setNodeKey("stage:one"); stage.setId(30L);
        var child=round(); child.setResultSnapshot("{\"projectEndUsers\":{\"projectId\":9,\"members\":[{\"assignmentId\":61}]}}");
        when(members.members(any())).thenReturn(List.of(member(61L,"END_USER",finished.minusHours(1))));
        var snapshot=new TemplateExecutionSnapshot();
        var s=new TemplateExecutionSnapshot.StageContract(); s.setNodeKey("stage:one"); s.setCode("S0"); s.setCompletionRuleKey("complete"); snapshot.setStages(List.of(s));
        var t=new TemplateExecutionSnapshot.TaskContract(); t.setNodeKey("task:one"); t.setCode("USER"); t.setStageCode("S0"); snapshot.setTasks(List.of(t));
        snapshot.getRulePrograms().put("complete",new cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram(
                cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule.Kind.CONDITION,"",List.of(new cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram.Leaf("leaf","rule","STAGE_NATIVE_STATUS",JsonUtils.parseTree("{}")))));
        var q=new ProjectExecutionTimeProjection.Query(7L,9L,1L,stage,snapshot,List.of(stage,child));
        assertEquals(finished.minusHours(1),service.project(q).at()); assertEquals(finished,stage.getEndedAt());
        child.setResultSnapshot("{}"); assertNull(service.project(q));
    }
}
