package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalControlledPorts;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec.ApprovalSourceSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec.AssessmentApprovalSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec.ChecklistResultSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec.CollectionAnalysisSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec.PlanApprovalSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec.ProjectApprovalSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CutoverApprovalSourceSnapshotCodecTest {

    private final CutoverApprovalSourceSnapshotCodec codec = new CutoverApprovalSourceSnapshotCodec();

    @Test
    void roundTripsExactSnapshotWithNullableNetworkModeAndStableItemOrder() {
        ApprovalSourceSnapshot source = snapshot("A", null);

        String encoded = codec.encode(source);
        ApprovalSourceSnapshot decoded = codec.decode(encoded);

        assertEquals(source, decoded);
        assertNull(decoded.collectionAnalysis().networkMode());
        assertEquals(List.of("risk-1", "risk-2"), decoded.riskItems().stream()
                .map(ChecklistResultSnapshot::stableItemKey).toList());
        assertEquals(5102L, decoded.riskItems().get(1).collectionResultReferenceId());
        assertEquals(3L, decoded.riskItems().get(1).collectionResultVersion());
        assertEquals(List.of("survey-1"), decoded.businessSurveyItems().stream()
                .map(ChecklistResultSnapshot::stableItemKey).toList());
        assertEquals(4, CutoverApprovalRules.routeFor("A").size());
        assertEquals(3, CutoverApprovalRules.routeFor("B").size());
        assertEquals(2, CutoverApprovalRules.routeFor("C").size());
        assertEquals(2, CutoverApprovalRules.routeFor("D").size());
    }

    @Test
    void controlledCandidatesKeepCompleteSetWhileProjectScopeSelectsOneUser() {
        var candidates = CutoverApprovalControlledPorts.roleCandidates()
                .inspectCandidates(1L, "CUT_SECOND_LINE_APPROVER");
        var scope = CutoverApprovalControlledPorts.projectScope(202L);

        List<Long> visible = candidates.candidates().stream()
                .filter(candidate -> scope.inspect(1L, 10L, candidate.adminUserId(), "ACTION_VIEW").allowed())
                .map(candidate -> candidate.adminUserId()).toList();

        assertEquals(List.of(201L, 202L), candidates.candidates().stream()
                .map(candidate -> candidate.adminUserId()).toList());
        assertEquals(List.of(202L), visible);
    }

    @Test
    void roundTripsGradeDSnapshotWithoutChecklistOrCollectionItems() {
        ApprovalSourceSnapshot decoded = codec.decode(codec.encode(snapshot("D", "DUAL_STACK")));

        assertEquals("D", decoded.assessment().manualGrade());
        assertNull(decoded.checklistId());
        assertEquals(List.of(), decoded.riskItems());
        assertEquals(List.of(), decoded.businessSurveyItems());
    }

    private static ApprovalSourceSnapshot snapshot(String grade, String networkMode) {
        List<ChecklistResultSnapshot> riskItems = "D".equals(grade) ? List.of() : List.of(
                collectionChecklist(102L, "risk-2", "RISK"),
                checklist(101L, "risk-1", "DUAL_MACHINE_CHECK"));
        List<ChecklistResultSnapshot> surveys = "D".equals(grade) ? List.of()
                : List.of(checklist(201L, "survey-1", "BUSINESS_SURVEY"));
        return new ApprovalSourceSnapshot(1, 1001L, 5,
                "D".equals(grade) ? null : 2001L, "D".equals(grade) ? null : 3,
                new ProjectApprovalSnapshot(3001L, 4, "P-001", "Project One", 4001L,
                        "C-001", "Customer One", 5001L, "OFF-01", "Office One", 12L),
                new CollectionAnalysisSnapshot("VERSION_UPGRADE", networkMode, 1_788_000_000_000L),
                riskItems, surveys,
                new AssessmentApprovalSnapshot(6001L, 2, 7001L, "HIGH", "MEDIUM", "LOW",
                        false, "GOLD", grade, 8001L, 1_787_000_000_000L),
                new PlanApprovalSnapshot(9001L, 1, 2,
                        JsonUtils.parseTree(planSource(grade)), JsonUtils.parseTree(uploadedPlan())));
    }

    private static ChecklistResultSnapshot checklist(long id, String key, String type) {
        return new ChecklistResultSnapshot(id, key, id + 1000, 1, type, "Item " + key,
                true, 1, "DIRECT", "{\"answer\":\"YES\"}", "confirmed",
                null, null, null, null, null);
    }

    private static ChecklistResultSnapshot collectionChecklist(long id, String key, String type) {
        return new ChecklistResultSnapshot(id, key, id + 1000, 1, type, "Item " + key,
                true, 1, "COLLECTION", "{\"answer\":\"YES\"}", "confirmed",
                4102L, 5102L, 3L, null, null);
    }

    private static String planSource(String grade) {
        String checklistId = "D".equals(grade) ? "null" : "2001";
        String checklistVersion = "D".equals(grade) ? "null" : "3";
        String sections = "D".equals(grade)
                ? "[{\"stableSectionKey\":\"OPERATION\",\"title\":\"Operation\",\"sortOrder\":1,\"cutoverTypeCodes\":[\"VERSION_UPGRADE\"],\"levelCodes\":[\"D\"],\"required\":true},{\"stableSectionKey\":\"ROLLBACK\",\"title\":\"Rollback\",\"sortOrder\":2,\"cutoverTypeCodes\":[\"VERSION_UPGRADE\"],\"levelCodes\":[\"D\"],\"required\":true}]"
                : "[{\"stableSectionKey\":\"OPERATION\",\"title\":\"Operation\",\"sortOrder\":1,\"cutoverTypeCodes\":[\"VERSION_UPGRADE\"],\"levelCodes\":[\"A\",\"B\",\"C\"],\"required\":true}]";
        return "{\"snapshotVersion\":1,\"taskId\":1001,\"taskVersion\":5,"
                + "\"assessmentId\":6001,\"assessmentVersion\":2,\"grade\":\"" + grade + "\","
                + "\"checklistId\":" + checklistId + ",\"checklistVersion\":" + checklistVersion + ","
                + "\"projectId\":3001,\"projectVersion\":4,\"projectScopeVersion\":12,"
                + "\"devices\":[{\"deviceId\":3101,\"serialNumber\":\"SN-001\",\"projectAssignmentVersion\":2,"
                + "\"deviceTypeCode\":\"ROUTER\",\"deviceTypeSourceVersion\":\"TYPE-V1\"}],"
                + "\"configurationRevisionId\":3201,\"configurationCode\":\"CUT-CONF\","
                + "\"configurationRevisionNo\":1,\"templateSections\":" + sections + ",\"failedRiskFacts\":[]}";
    }

    private static String uploadedPlan() {
        return "{\"editMode\":\"FULL_FILE_UPLOAD\",\"fileArtifactFact\":{\"artifactId\":3301,"
                + "\"versionNo\":1,\"referenceKey\":\"cut-plan-file\",\"fileFactVersion\":{"
                + "\"artifactVersion\":1,\"referenceVersion\":1,\"availabilityVersion\":1},"
                + "\"scopeVersion\":2,\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"},"
                + "\"ownershipConfirmed\":true}";
    }
}
