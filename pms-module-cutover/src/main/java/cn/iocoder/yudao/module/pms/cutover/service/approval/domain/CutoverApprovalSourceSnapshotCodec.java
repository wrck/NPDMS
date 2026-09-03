package cn.iocoder.yudao.module.pms.cutover.service.approval.domain;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanContentCodec;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules.GRADES;
import static cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules.require;
import static cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalRules.requireText;

public final class CutoverApprovalSourceSnapshotCodec {

    private static final long MAX_SAFE_WIRE_LONG = 9_007_199_254_740_991L;
    private static final Set<String> ROOT = Set.of("snapshotVersion", "taskId", "taskVersion", "checklistId",
            "checklistVersion", "project", "collectionAnalysis", "riskItems", "businessSurveyItems",
            "assessment", "plan");
    private static final Set<String> PROJECT = Set.of("projectId", "projectVersion", "projectCode", "projectName",
            "customerId", "customerCode", "customerName", "officeDepartmentId", "officeCode", "officeName",
            "projectScopeVersion");
    private static final Set<String> COLLECTION = Set.of("cutoverType", "networkMode", "scheduledTime");
    private static final Set<String> CHECKLIST = Set.of("checklistItemId", "stableItemKey", "itemDefinitionId",
            "itemDefinitionVersion", "itemTypeCode", "itemName", "required", "itemResultVersion",
            "resultSourceCode", "answerSnapshot", "factDescription", "collectionTaskId",
            "collectionResultReferenceId", "collectionResultVersion", "externalSourceCode",
            "manualEvidenceFileReference");
    private static final Set<String> ASSESSMENT = Set.of("assessmentId", "assessmentVersion",
            "questionnaireTemplateCode", "questionnaireTemplateVersion", "businessImportanceLevel",
            "operationComplexityLevel", "hiddenRiskLevel", "sparePartApplied", "customerServiceLevelCode",
            "manualGrade", "submittedBy", "submittedAt");
    private static final Set<String> PLAN = Set.of("planRevisionId", "planRevisionNo", "planVersion",
            "originCode", "sourceSnapshot", "content");
    private static final Set<String> PLAN_SOURCE = Set.of("snapshotVersion", "taskId", "taskVersion",
            "assessmentId", "assessmentVersion", "grade", "checklistId", "checklistVersion", "projectId",
            "projectVersion", "projectScopeVersion", "devices", "configurationRevisionId", "configurationCode",
            "configurationRevisionNo", "templateSections", "failedRiskFacts");
    private static final Set<String> PLAN_DEVICE = Set.of("deviceId", "serialNumber", "projectAssignmentVersion",
            "deviceTypeCode", "deviceTypeSourceVersion");
    private static final Set<String> PLAN_SECTION = Set.of("stableSectionKey", "title", "sortOrder",
            "cutoverTypeCodes", "levelCodes", "required");
    private static final Set<String> PLAN_RISK = Set.of("checklistItemId", "stableItemKey", "itemResultVersion",
            "itemName", "resultCode", "factDescription");
    private static final Set<String> ITEM_TYPES = Set.of("RISK", "DUAL_MACHINE_CHECK", "BUSINESS_SURVEY");
    private static final Set<String> RESULT_SOURCES = Set.of("DIRECT", "COLLECTION", "EXTERNAL", "MANUAL");

    public String encode(ApprovalSourceSnapshot snapshot) {
        JsonNode json = toJson(snapshot);
        decode(json);
        return JsonUtils.toJsonString(json);
    }

    public ApprovalSourceSnapshot decode(String json) {
        require(json != null && !json.isBlank(), "sourceSnapshot");
        return decode(JsonUtils.parseTree(json));
    }

    public ApprovalSourceSnapshot decode(JsonNode root) {
        exact(root, ROOT, "sourceSnapshot");
        Long checklistId = nullablePositiveLong(root.get("checklistId"), "checklistId");
        Integer checklistVersion = nullablePositiveInt(root.get("checklistVersion"), "checklistVersion");
        return new ApprovalSourceSnapshot(
                positiveInt(root, "snapshotVersion"), positiveLong(root, "taskId"),
                nonNegativeInt(root, "taskVersion"), checklistId, checklistVersion,
                project(root.get("project")), collection(root.get("collectionAnalysis")),
                checklistItems(root.get("riskItems"), Set.of("RISK", "DUAL_MACHINE_CHECK")),
                checklistItems(root.get("businessSurveyItems"), Set.of("BUSINESS_SURVEY")),
                assessment(root.get("assessment")), plan(root.get("plan")));
    }

    public JsonNode toJson(ApprovalSourceSnapshot snapshot) {
        require(snapshot != null, "sourceSnapshot");
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("snapshotVersion", snapshot.snapshotVersion());
        putWireLong(root, "taskId", snapshot.taskId());
        root.put("taskVersion", snapshot.taskVersion());
        putNullableWireLong(root, "checklistId", snapshot.checklistId());
        putNullableInt(root, "checklistVersion", snapshot.checklistVersion());
        root.set("project", projectJson(snapshot.project()));
        root.set("collectionAnalysis", collectionJson(snapshot.collectionAnalysis()));
        root.set("riskItems", checklistJson(snapshot.riskItems()));
        root.set("businessSurveyItems", checklistJson(snapshot.businessSurveyItems()));
        root.set("assessment", assessmentJson(snapshot.assessment()));
        root.set("plan", planJson(snapshot.plan()));
        return root;
    }

    private ProjectApprovalSnapshot project(JsonNode node) {
        exact(node, PROJECT, "project");
        return new ProjectApprovalSnapshot(positiveLong(node, "projectId"), nonNegativeInt(node, "projectVersion"),
                text(node, "projectCode", 64), text(node, "projectName", 255),
                positiveLong(node, "customerId"), text(node, "customerCode", 64),
                text(node, "customerName", 255), positiveLong(node, "officeDepartmentId"),
                text(node, "officeCode", 64), text(node, "officeName", 255),
                nonNegativeLong(node, "projectScopeVersion"));
    }

    private ObjectNode projectJson(ProjectApprovalSnapshot value) {
        ObjectNode node = JsonUtils.getObjectMapper().createObjectNode();
        putWireLong(node, "projectId", value.projectId()); node.put("projectVersion", value.projectVersion());
        node.put("projectCode", value.projectCode()); node.put("projectName", value.projectName());
        putWireLong(node, "customerId", value.customerId()); node.put("customerCode", value.customerCode());
        node.put("customerName", value.customerName()); putWireLong(node, "officeDepartmentId", value.officeDepartmentId());
        node.put("officeCode", value.officeCode()); node.put("officeName", value.officeName());
        putWireLong(node, "projectScopeVersion", value.projectScopeVersion()); return node;
    }

    private CollectionAnalysisSnapshot collection(JsonNode node) {
        exact(node, COLLECTION, "collectionAnalysis");
        return new CollectionAnalysisSnapshot(text(node, "cutoverType", 64), nullableText(node, "networkMode", 64),
                positiveLong(node, "scheduledTime"));
    }

    private ObjectNode collectionJson(CollectionAnalysisSnapshot value) {
        ObjectNode node = JsonUtils.getObjectMapper().createObjectNode(); node.put("cutoverType", value.cutoverType());
        if (value.networkMode() == null) node.putNull("networkMode"); else node.put("networkMode", value.networkMode());
        putWireLong(node, "scheduledTime", value.scheduledTime()); return node;
    }

    private List<ChecklistResultSnapshot> checklistItems(JsonNode node, Set<String> allowedTypes) {
        require(node != null && node.isArray(), "checklistItems");
        List<ChecklistResultSnapshot> result = new ArrayList<>();
        node.forEach(item -> {
            exact(item, CHECKLIST, "checklistItem");
            String itemType = text(item, "itemTypeCode", 32); require(allowedTypes.contains(itemType), "itemTypeCode");
            String source = text(item, "resultSourceCode", 32); require(RESULT_SOURCES.contains(source), "resultSourceCode");
            result.add(new ChecklistResultSnapshot(positiveLong(item, "checklistItemId"),
                    text(item, "stableItemKey", 128), nullablePositiveLong(item.get("itemDefinitionId"), "itemDefinitionId"),
                    nullablePositiveInt(item.get("itemDefinitionVersion"), "itemDefinitionVersion"), itemType,
                    text(item, "itemName", 255), booleanValue(item, "required"), positiveInt(item, "itemResultVersion"),
                    source, validJsonText(item, "answerSnapshot"), nullableText(item, "factDescription", 4000),
                    nullablePositiveLong(item.get("collectionTaskId"), "collectionTaskId"),
                    nullablePositiveLong(item.get("collectionResultReferenceId"), "collectionResultReferenceId"),
                    nullablePositiveLong(item.get("collectionResultVersion"), "collectionResultVersion"),
                    nullableText(item, "externalSourceCode", 64),
                    nullableText(item, "manualEvidenceFileReference", 128)));
        });
        return result;
    }

    private ArrayNode checklistJson(List<ChecklistResultSnapshot> values) {
        ArrayNode array = JsonUtils.getObjectMapper().createArrayNode();
        values.forEach(value -> {
            ObjectNode node = array.addObject(); putWireLong(node, "checklistItemId", value.checklistItemId());
            node.put("stableItemKey", value.stableItemKey()); putNullableWireLong(node, "itemDefinitionId", value.itemDefinitionId());
            putNullableInt(node, "itemDefinitionVersion", value.itemDefinitionVersion()); node.put("itemTypeCode", value.itemTypeCode());
            node.put("itemName", value.itemName()); node.put("required", value.required());
            node.put("itemResultVersion", value.itemResultVersion()); node.put("resultSourceCode", value.resultSourceCode());
            node.put("answerSnapshot", value.answerSnapshot()); putNullableText(node, "factDescription", value.factDescription());
            putNullableWireLong(node, "collectionTaskId", value.collectionTaskId());
            putNullableWireLong(node, "collectionResultReferenceId", value.collectionResultReferenceId());
            putNullableWireLong(node, "collectionResultVersion", value.collectionResultVersion());
            putNullableText(node, "externalSourceCode", value.externalSourceCode());
            putNullableText(node, "manualEvidenceFileReference", value.manualEvidenceFileReference());
        }); return array;
    }

    private AssessmentApprovalSnapshot assessment(JsonNode node) {
        exact(node, ASSESSMENT, "assessment");
        require("CUT_P2_MANUAL_ASSESSMENT".equals(text(node, "questionnaireTemplateCode", 64)),
                "questionnaireTemplateCode");
        return new AssessmentApprovalSnapshot(positiveLong(node, "assessmentId"),
                positiveInt(node, "assessmentVersion"), positiveLong(node, "questionnaireTemplateVersion"),
                text(node, "businessImportanceLevel", 64), text(node, "operationComplexityLevel", 64),
                text(node, "hiddenRiskLevel", 64), booleanValue(node, "sparePartApplied"),
                text(node, "customerServiceLevelCode", 64), text(node, "manualGrade", 1),
                positiveLong(node, "submittedBy"), positiveLong(node, "submittedAt"));
    }

    private ObjectNode assessmentJson(AssessmentApprovalSnapshot value) {
        ObjectNode node = JsonUtils.getObjectMapper().createObjectNode(); putWireLong(node, "assessmentId", value.assessmentId());
        node.put("assessmentVersion", value.assessmentVersion()); node.put("questionnaireTemplateCode", "CUT_P2_MANUAL_ASSESSMENT");
        putWireLong(node, "questionnaireTemplateVersion", value.questionnaireTemplateVersion());
        node.put("businessImportanceLevel", value.businessImportanceLevel());
        node.put("operationComplexityLevel", value.operationComplexityLevel()); node.put("hiddenRiskLevel", value.hiddenRiskLevel());
        node.put("sparePartApplied", value.sparePartApplied()); node.put("customerServiceLevelCode", value.customerServiceLevelCode());
        node.put("manualGrade", value.manualGrade()); putWireLong(node, "submittedBy", value.submittedBy());
        putWireLong(node, "submittedAt", value.submittedAt()); return node;
    }

    private PlanApprovalSnapshot plan(JsonNode node) {
        exact(node, PLAN, "plan"); require("NEW_PLATFORM".equals(text(node, "originCode", 32)), "originCode");
        CutoverPlanSourcePort.SourceFacts sourceFacts = validatedPlanSource(node.get("sourceSnapshot"));
        CutoverPlanContentCodec contentCodec = new CutoverPlanContentCodec();
        CutoverPlanContentCodec.DecodedContent content = contentCodec.decodeWritable(node.get("content"), sourceFacts);
        contentCodec.validateComplete(content, sourceFacts);
        return new PlanApprovalSnapshot(positiveLong(node, "planRevisionId"), positiveInt(node, "planRevisionNo"),
                nonNegativeInt(node, "planVersion"), canonical(node.get("sourceSnapshot")), canonical(node.get("content")));
    }

    private static CutoverPlanSourcePort.SourceFacts validatedPlanSource(JsonNode node) {
        exact(node, PLAN_SOURCE, "plan.sourceSnapshot");
        List<CutoverPlanSourcePort.DeviceSnapshot> devices = new ArrayList<>();
        JsonNode deviceArray = node.get("devices"); require(deviceArray != null && deviceArray.isArray(), "plan.devices");
        deviceArray.forEach(device -> {
            exact(device, PLAN_DEVICE, "plan.device");
            devices.add(new CutoverPlanSourcePort.DeviceSnapshot(positiveLong(device, "deviceId"),
                    identityText(device, "serialNumber", 128), nonNegativeLong(device, "projectAssignmentVersion"),
                    text(device, "deviceTypeCode", 64), text(device, "deviceTypeSourceVersion", 128)));
        });
        List<CutoverPlanSourcePort.TemplateSectionSnapshot> sections = new ArrayList<>();
        JsonNode sectionArray = node.get("templateSections"); require(sectionArray != null && sectionArray.isArray(), "plan.templateSections");
        sectionArray.forEach(section -> {
            exact(section, PLAN_SECTION, "plan.templateSection");
            sections.add(new CutoverPlanSourcePort.TemplateSectionSnapshot(text(section, "stableSectionKey", 64),
                    text(section, "title", 128), nonNegativeInt(section, "sortOrder"),
                    stringList(section.get("cutoverTypeCodes"), 64, "cutoverTypeCodes"),
                    stringList(section.get("levelCodes"), 1, "levelCodes"), booleanValue(section, "required")));
        });
        List<CutoverPlanSourcePort.RiskFactSnapshot> risks = new ArrayList<>();
        JsonNode riskArray = node.get("failedRiskFacts"); require(riskArray != null && riskArray.isArray(), "plan.failedRiskFacts");
        riskArray.forEach(risk -> {
            exact(risk, PLAN_RISK, "plan.riskFact");
            risks.add(new CutoverPlanSourcePort.RiskFactSnapshot(positiveLong(risk, "checklistItemId"),
                    text(risk, "stableItemKey", 128), positiveInt(risk, "itemResultVersion"),
                    text(risk, "itemName", 255), text(risk, "resultCode", 32),
                    text(risk, "factDescription", 4000)));
        });
        String grade = text(node, "grade", 1);
        CutoverPlanSourcePort.SourceSnapshot snapshot = new CutoverPlanSourcePort.SourceSnapshot(
                positiveInt(node, "snapshotVersion"), positiveLong(node, "taskId"), nonNegativeInt(node, "taskVersion"),
                positiveLong(node, "assessmentId"), positiveInt(node, "assessmentVersion"), grade,
                nullablePositiveLong(node.get("checklistId"), "checklistId"),
                nullablePositiveInt(node.get("checklistVersion"), "checklistVersion"),
                positiveLong(node, "projectId"), nonNegativeInt(node, "projectVersion"),
                nonNegativeLong(node, "projectScopeVersion"), devices,
                positiveLong(node, "configurationRevisionId"), text(node, "configurationCode", 64),
                positiveInt(node, "configurationRevisionNo"), sections, risks);
        return new CutoverPlanSourcePort.SourceFacts(snapshot, risks);
    }

    private static List<String> stringList(JsonNode node, int max, String field) {
        require(node != null && node.isArray() && !node.isEmpty(), field);
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            require(value.isTextual(), field);
            values.add(requireText(value.asText(), max, field));
        });
        return values;
    }

    private ObjectNode planJson(PlanApprovalSnapshot value) {
        ObjectNode node = JsonUtils.getObjectMapper().createObjectNode(); putWireLong(node, "planRevisionId", value.planRevisionId());
        node.put("planRevisionNo", value.planRevisionNo()); node.put("planVersion", value.planVersion());
        node.put("originCode", "NEW_PLATFORM"); node.set("sourceSnapshot", canonical(value.sourceSnapshot()));
        node.set("content", canonical(value.content())); return node;
    }

    private static JsonNode canonical(JsonNode node) {
        require(node != null, "json");
        if (node.isObject()) {
            ObjectNode result = JsonUtils.getObjectMapper().createObjectNode();
            node.properties().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> result.set(entry.getKey(), canonical(entry.getValue())));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonUtils.getObjectMapper().createArrayNode(); node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node.deepCopy();
    }

    private static void exact(JsonNode node, Set<String> keys, String field) {
        require(node != null && node.isObject(), field);
        Set<String> actual = new HashSet<>(); node.properties().forEach(entry -> actual.add(entry.getKey()));
        require(actual.equals(keys), field + " keys");
    }
    private static String text(JsonNode node, String field, int max) { String value=node.path(field).isTextual()?node.path(field).asText():null; return requireText(value,max,field); }
    private static String identityText(JsonNode node,String field,int max){JsonNode value=node.get(field);String text=value!=null&&value.isTextual()?value.asText():null;require(text!=null&&!text.trim().isEmpty()&&text.length()<=max,field);return text;}
    private static String nullableText(JsonNode node,String field,int max){JsonNode value=node.get(field);if(value==null||value.isNull())return null;return text(node,field,max);}
    private static boolean booleanValue(JsonNode node,String field){require(node.path(field).isBoolean(),field);return node.path(field).asBoolean();}
    private static int positiveInt(JsonNode node,String field){int value=node.path(field).isInt()?node.path(field).asInt():0;require(value>0,field);return value;}
    private static int nonNegativeInt(JsonNode node,String field){int value=node.path(field).isInt()?node.path(field).asInt():-1;require(value>=0,field);return value;}
    private static Integer nullablePositiveInt(JsonNode node,String field){if(node==null||node.isNull())return null;require(node.isInt()&&node.asInt()>0,field);return node.asInt();}
    private static long positiveLong(JsonNode node,String field){long value=wireLong(node.path(field),field);require(value>0,field);return value;}
    private static long nonNegativeLong(JsonNode node,String field){long value=wireLong(node.path(field),field);require(value>=0,field);return value;}
    private static Long nullablePositiveLong(JsonNode node,String field){if(node==null||node.isNull())return null;long value=wireLong(node,field);require(value>0,field);return value;}
    private static long wireLong(JsonNode node,String field){
        if(node!=null&&node.isIntegralNumber()){require(node.canConvertToLong(),field);long value=node.asLong();require(value>-MAX_SAFE_WIRE_LONG&&value<MAX_SAFE_WIRE_LONG,field);return value;}
        require(node!=null&&node.isTextual()&&node.asText().matches("-?(0|[1-9][0-9]*)"),field);
        try{return Long.parseLong(node.asText());}catch(NumberFormatException ex){throw new IllegalArgumentException("invalid "+field,ex);}
    }
    private static String validJsonText(JsonNode node,String field){JsonNode valueNode=node.get(field);String value=valueNode!=null&&valueNode.isTextual()?valueNode.asText():null;require(value!=null&&!value.isBlank(),field);JsonUtils.parseTree(value);return value;}
    private static void putWireLong(ObjectNode node,String field,long value){if(value>-MAX_SAFE_WIRE_LONG&&value<MAX_SAFE_WIRE_LONG)node.put(field,value);else node.put(field,Long.toString(value));}
    private static void putNullableWireLong(ObjectNode node,String field,Long value){if(value==null)node.putNull(field);else putWireLong(node,field,value);}
    private static void putNullableInt(ObjectNode node,String field,Integer value){if(value==null)node.putNull(field);else node.put(field,value);}
    private static void putNullableText(ObjectNode node,String field,String value){if(value==null)node.putNull(field);else node.put(field,value);}

    public record ApprovalSourceSnapshot(int snapshotVersion, long taskId, int taskVersion, Long checklistId,
                                         Integer checklistVersion, ProjectApprovalSnapshot project,
                                         CollectionAnalysisSnapshot collectionAnalysis,
                                         List<ChecklistResultSnapshot> riskItems,
                                         List<ChecklistResultSnapshot> businessSurveyItems,
                                         AssessmentApprovalSnapshot assessment, PlanApprovalSnapshot plan) {
        public ApprovalSourceSnapshot {
            require(snapshotVersion > 0 && taskId > 0 && taskVersion >= 0, "sourceSnapshotIdentity");
            require(project != null && collectionAnalysis != null && assessment != null && plan != null, "sourceSnapshotParts");
            require(assessment.manualGrade().equals("D") == (checklistId == null && checklistVersion == null), "checklistIdentity");
            if (!"D".equals(assessment.manualGrade())) require(checklistId != null && checklistId > 0 && checklistVersion != null && checklistVersion > 0, "checklistIdentity");
            require(riskItems != null && businessSurveyItems != null, "checklistItems");
            riskItems = sortedItems(riskItems, Set.of("RISK", "DUAL_MACHINE_CHECK"));
            businessSurveyItems = sortedItems(businessSurveyItems, Set.of("BUSINESS_SURVEY"));
            require(!"D".equals(assessment.manualGrade()) || (riskItems.isEmpty() && businessSurveyItems.isEmpty()), "gradeDItems");
            CutoverPlanSourcePort.SourceSnapshot planSource = validatedPlanSource(plan.sourceSnapshot()).snapshot();
            require(planSource.taskId() == taskId && planSource.taskVersion() == taskVersion,
                    "planTaskIdentity");
            require(planSource.projectId() == project.projectId()
                            && planSource.projectVersion() == project.projectVersion()
                            && planSource.projectScopeVersion() == project.projectScopeVersion(),
                    "planProjectIdentity");
            require(Objects.equals(planSource.checklistId(), checklistId)
                            && Objects.equals(planSource.checklistVersion(), checklistVersion),
                    "planChecklistIdentity");
            require(planSource.assessmentId() == assessment.assessmentId()
                            && planSource.assessmentVersion() == assessment.assessmentVersion()
                            && planSource.grade().equals(assessment.manualGrade()),
                    "planAssessmentIdentity");
        }
    }

    private static List<ChecklistResultSnapshot> sortedItems(List<ChecklistResultSnapshot> values,Set<String> types){
        require(values.stream().allMatch(value->types.contains(value.itemTypeCode())),"itemTypeCode");
        List<ChecklistResultSnapshot> sorted=values.stream().sorted(Comparator.comparing(ChecklistResultSnapshot::stableItemKey)).toList();
        require(new HashSet<>(sorted.stream().map(ChecklistResultSnapshot::stableItemKey).toList()).size()==sorted.size(),"stableItemKey");return sorted;
    }

    public record ProjectApprovalSnapshot(long projectId,int projectVersion,String projectCode,String projectName,
                                            long customerId,String customerCode,String customerName,long officeDepartmentId,
                                            String officeCode,String officeName,long projectScopeVersion){public ProjectApprovalSnapshot{require(projectId>0&&projectVersion>=0&&customerId>0&&officeDepartmentId>0&&projectScopeVersion>=0,"project");requireText(projectCode,64,"projectCode");requireText(projectName,255,"projectName");requireText(customerCode,64,"customerCode");requireText(customerName,255,"customerName");requireText(officeCode,64,"officeCode");requireText(officeName,255,"officeName");}}
    public record CollectionAnalysisSnapshot(String cutoverType,String networkMode,long scheduledTime){public CollectionAnalysisSnapshot{requireText(cutoverType,64,"cutoverType");if(networkMode!=null)requireText(networkMode,64,"networkMode");require(scheduledTime>0,"scheduledTime");}}
    public record ChecklistResultSnapshot(long checklistItemId,String stableItemKey,Long itemDefinitionId,Integer itemDefinitionVersion,String itemTypeCode,String itemName,boolean required,int itemResultVersion,String resultSourceCode,String answerSnapshot,String factDescription,Long collectionTaskId,Long collectionResultReferenceId,Long collectionResultVersion,String externalSourceCode,String manualEvidenceFileReference){public ChecklistResultSnapshot{require(checklistItemId>0&&itemResultVersion>0,"checklistItem");requireText(stableItemKey,128,"stableItemKey");require(ITEM_TYPES.contains(itemTypeCode),"itemTypeCode");requireText(itemName,255,"itemName");require(RESULT_SOURCES.contains(resultSourceCode),"resultSourceCode");require(answerSnapshot!=null&&!answerSnapshot.isBlank(),"answerSnapshot");if(factDescription!=null)requireText(factDescription,4000,"factDescription");require((itemDefinitionId==null)==(itemDefinitionVersion==null),"itemDefinition");require(itemDefinitionId==null||itemDefinitionId>0,"itemDefinitionId");require(itemDefinitionVersion==null||itemDefinitionVersion>0,"itemDefinitionVersion");require(("COLLECTION".equals(resultSourceCode)&&collectionTaskId!=null&&collectionTaskId>0)||(!"COLLECTION".equals(resultSourceCode)&&collectionTaskId==null&&collectionResultReferenceId==null&&collectionResultVersion==null),"collectionIdentity");require(collectionResultReferenceId==null||collectionResultReferenceId>0,"collectionResultReferenceId");require(collectionResultVersion==null||collectionResultVersion>0,"collectionResultVersion");require(("EXTERNAL".equals(resultSourceCode))==(externalSourceCode!=null),"externalSourceCode");if(externalSourceCode!=null)requireText(externalSourceCode,64,"externalSourceCode");if(manualEvidenceFileReference!=null)require("MANUAL".equals(resultSourceCode)&&manualEvidenceFileReference.length()<=128&&!manualEvidenceFileReference.isBlank(),"manualEvidenceFileReference");}}
    public record AssessmentApprovalSnapshot(long assessmentId,int assessmentVersion,long questionnaireTemplateVersion,String businessImportanceLevel,String operationComplexityLevel,String hiddenRiskLevel,boolean sparePartApplied,String customerServiceLevelCode,String manualGrade,long submittedBy,long submittedAt){public AssessmentApprovalSnapshot{require(assessmentId>0&&assessmentVersion>0&&questionnaireTemplateVersion>0&&submittedBy>0&&submittedAt>0,"assessment");requireText(businessImportanceLevel,64,"businessImportanceLevel");requireText(operationComplexityLevel,64,"operationComplexityLevel");requireText(hiddenRiskLevel,64,"hiddenRiskLevel");requireText(customerServiceLevelCode,64,"customerServiceLevelCode");require(GRADES.contains(manualGrade),"manualGrade");}}
    public record PlanApprovalSnapshot(long planRevisionId,int planRevisionNo,int planVersion,JsonNode sourceSnapshot,JsonNode content){public PlanApprovalSnapshot{require(planRevisionId>0&&planRevisionNo>0&&planVersion>=0,"plan");require(sourceSnapshot!=null&&sourceSnapshot.isObject()&&content!=null&&content.isObject(),"planContent");sourceSnapshot=canonical(sourceSnapshot);content=canonical(content);}}
}
