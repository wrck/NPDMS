package cn.iocoder.yudao.module.pms.cutover.service.plan.domain;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;

import static cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules.require;

public final class CutoverPlanContentCodec {
    private static final long MAX_SAFE_WIRE_LONG = 9_007_199_254_740_991L;
    private static final Set<String> STANDARD = Set.of("editMode", "overview", "steps", "riskMitigations", "supportArrangements");
    private static final Set<String> SIMPLE = Set.of("editMode", "steps");
    private static final Set<String> UPLOAD = Set.of("editMode", "fileArtifactFact", "ownershipConfirmed");
    private static final Set<String> STEP = Set.of("sectionCode", "stepNo", "content");
    private static final Set<String> SUPPORT = Set.of("arrangementId", "roleCode", "personName", "dutyDescription", "phone", "arrivalTime");
    private static final Set<String> FILE = Set.of("artifactId", "versionNo", "referenceKey", "fileFactVersion", "scopeVersion", "sha256");
    private static final Set<String> FILE_VERSION = Set.of("artifactVersion", "referenceVersion", "availabilityVersion");
    private static final Set<String> OVERVIEW = Set.of("projectDescription", "scheduleTable", "preTopologyFile",
            "postTopologyFile", "deviceSummary", "networkConfigurationFile");
    private static final Set<String> SCHEDULE = Set.of("sequenceNo", "plannedAt", "content");
    private static final Set<String> DEVICE = Set.of("deviceId", "serialNumber", "projectAssignmentVersion",
            "deviceTypeCode", "deviceTypeSourceVersion");
    private static final Set<String> MITIGATION = Set.of("riskFact", "mitigation");
    private static final Set<String> RISK = Set.of("checklistItemId", "stableItemKey", "itemResultVersion",
            "itemName", "resultCode", "factDescription");
    private static final List<String> SECTION_ORDER = CutoverPlanRules.STANDARD_SECTIONS;

    public DecodedContent createInitialOnlineDraft(String editMode, CutoverPlanSourcePort.SourceFacts sourceFacts) {
        require(sourceFacts != null, "sourceFacts");
        ObjectNode body = JsonUtils.getObjectMapper().createObjectNode();
        body.put("editMode", editMode);
        body.putArray("steps");
        if ("ONLINE_TEMPLATE_SIMPLE_D".equals(editMode)) {
            return decodeWritable(body, sourceFacts);
        }
        require("ONLINE_TEMPLATE_STANDARD".equals(editMode), "editMode");
        ObjectNode overview = body.putObject("overview");
        overview.put("projectDescription", "");
        overview.putArray("scheduleTable");
        overview.putNull("preTopologyFile");
        overview.putNull("postTopologyFile");
        overview.set("deviceSummary", ownerDevices(sourceFacts.snapshot()));
        overview.putNull("networkConfigurationFile");
        body.putArray("riskMitigations");
        body.putArray("supportArrangements");
        return decodeWritable(body, sourceFacts);
    }

    public DecodedContent rebaseDerivedDraft(DecodedContent source,
                                             CutoverPlanSourcePort.SourceFacts currentFacts) {
        require(source != null && currentFacts != null, "derived draft");
        if (source.fileFact() != null) {
            return source;
        }
        String editMode = source.rootSnapshot().path("editMode").asText();
        ObjectNode body = JsonUtils.getObjectMapper().createObjectNode();
        body.put("editMode", editMode);
        ArrayNode steps = body.putArray("steps");
        source.steps().forEach(step -> {
            ObjectNode row = steps.addObject();
            row.put("sectionCode", step.sectionCode());
            row.put("stepNo", step.stepNo());
            row.put("content", step.content());
        });
        if ("ONLINE_TEMPLATE_SIMPLE_D".equals(editMode)) {
            return decodeWritable(body, currentFacts);
        }
        require("ONLINE_TEMPLATE_STANDARD".equals(editMode), "derived editMode");
        ObjectNode overview = (ObjectNode) source.rootSnapshot().path("overview").deepCopy();
        overview.set("deviceSummary", ownerDevices(currentFacts.snapshot()));
        body.set("overview", overview);
        body.set("riskMitigations", rebasedRiskMitigations(source.rootSnapshot().path("riskMitigations"),
                currentFacts.failedRiskFacts()));
        ArrayNode support = body.putArray("supportArrangements");
        source.supportArrangements().forEach(arrangement -> {
            ObjectNode row = support.addObject();
            row.putNull("arrangementId");
            row.put("roleCode", arrangement.roleCode());
            row.put("personName", arrangement.personName());
            row.put("dutyDescription", arrangement.dutyDescription());
            row.put("phone", arrangement.phone());
            putWireLong(row, "arrivalTime", arrangement.arrivalTime());
        });
        return decodeWritable(body, currentFacts);
    }

    private static ArrayNode ownerDevices(CutoverPlanSourcePort.SourceSnapshot source) {
        ArrayNode devices = JsonUtils.getObjectMapper().createArrayNode();
        source.devices().forEach(device -> {
            ObjectNode row = devices.addObject();
            putWireLong(row, "deviceId", device.deviceId());
            row.put("serialNumber", device.serialNumber());
            putWireLong(row, "projectAssignmentVersion", device.projectAssignmentVersion());
            row.put("deviceTypeCode", device.deviceTypeCode());
            row.put("deviceTypeSourceVersion", device.deviceTypeSourceVersion());
        });
        return devices;
    }

    private static ArrayNode rebasedRiskMitigations(JsonNode source,
                                                     List<CutoverPlanSourcePort.RiskFactSnapshot> currentFacts) {
        Map<String, String> mitigationByKey = new HashMap<>();
        source.forEach(row -> mitigationByKey.put(row.path("riskFact").path("stableItemKey").asText(),
                row.path("mitigation").asText()));
        ArrayNode result = JsonUtils.getObjectMapper().createArrayNode();
        currentFacts.forEach(fact -> {
            String mitigation = mitigationByKey.get(fact.stableItemKey());
            if (mitigation == null) return;
            ObjectNode row = result.addObject();
            ObjectNode frozen = row.putObject("riskFact");
            putWireLong(frozen, "checklistItemId", fact.checklistItemId());
            frozen.put("stableItemKey", fact.stableItemKey());
            frozen.put("itemResultVersion", fact.itemResultVersion());
            frozen.put("itemName", fact.itemName());
            frozen.put("resultCode", fact.resultCode());
            frozen.put("factDescription", fact.factDescription());
            row.put("mitigation", mitigation);
        });
        return result;
    }

    public DecodedContent decodeWritable(JsonNode body, CutoverPlanSourcePort.SourceFacts sourceFacts) {
        require(body != null && body.isObject(), "content");
        require(sourceFacts != null, "sourceFacts");
        String mode = text(body, "editMode", 32);
        return switch (mode) {
            case "ONLINE_TEMPLATE_STANDARD" -> {
                require(!"D".equals(sourceFacts.snapshot().grade()), "standard grade");
                yield decodeOnline(body, true, sourceFacts);
            }
            case "ONLINE_TEMPLATE_SIMPLE_D" -> {
                require("D".equals(sourceFacts.snapshot().grade()), "simple grade");
                yield decodeOnline(body, false);
            }
            case "FULL_FILE_UPLOAD" -> decodeUpload(body);
            default -> throw new IllegalArgumentException("invalid editMode");
        };
    }

    private DecodedContent decodeOnline(JsonNode body, boolean standard) {
        return decodeOnline(body, standard, null);
    }

    private DecodedContent decodeOnline(JsonNode body, boolean standard, CutoverPlanSourcePort.SourceFacts sourceFacts) {
        exact(body, standard ? STANDARD : SIMPLE, "content");
        List<PlanStep> steps = steps(body.get("steps"), standard ? CutoverPlanRules.STANDARD_SECTIONS : CutoverPlanRules.SIMPLE_SECTIONS);
        List<SupportArrangement> support = standard ? supportDraft(body.get("supportArrangements")) : List.of();
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("editMode", standard ? "ONLINE_TEMPLATE_STANDARD" : "ONLINE_TEMPLATE_SIMPLE_D");
        if (standard) {
            root.set("overview", overviewDraft(body.get("overview"), sourceFacts.snapshot()));
            root.set("riskMitigations", riskMitigationsDraft(body.get("riskMitigations"), sourceFacts.failedRiskFacts()));
        }
        return new DecodedContent(root, steps, support, null, false);
    }

    private ObjectNode overviewDraft(JsonNode node, CutoverPlanSourcePort.SourceSnapshot source) {
        exact(node, OVERVIEW, "overview");
        ObjectNode result = JsonUtils.getObjectMapper().createObjectNode();
        result.put("projectDescription", draftText(node, "projectDescription", 4000));
        result.set("scheduleTable", scheduleDraft(node.get("scheduleTable")));
        result.set("preTopologyFile", nullableFile(node.get("preTopologyFile")));
        result.set("postTopologyFile", nullableFile(node.get("postTopologyFile")));
        result.set("deviceSummary", deviceSummary(node.get("deviceSummary"), source));
        result.set("networkConfigurationFile", nullableFile(node.get("networkConfigurationFile")));
        return result;
    }

    private ArrayNode scheduleDraft(JsonNode array) {
        require(array != null && array.isArray(), "scheduleTable");
        List<ObjectNode> rows = new ArrayList<>();
        Set<Integer> numbers = new HashSet<>();
        array.forEach(node -> {
            exact(node, SCHEDULE, "scheduleRow");
            int no = positiveInt(node, "sequenceNo"); require(numbers.add(no), "sequenceNo");
            ObjectNode row = JsonUtils.getObjectMapper().createObjectNode();
            row.put("sequenceNo", no); putWireLong(row, "plannedAt", positiveLong(node, "plannedAt"));
            row.put("content", text(node, "content", 1000)); rows.add(row);
        });
        rows.sort(Comparator.comparingInt(row -> row.path("sequenceNo").asInt()));
        ArrayNode result = JsonUtils.getObjectMapper().createArrayNode(); rows.forEach(result::add); return result;
    }

    private ArrayNode deviceSummary(JsonNode array, CutoverPlanSourcePort.SourceSnapshot source) {
        require(array != null && array.isArray() && array.size() == source.devices().size(), "deviceSummary");
        Map<Long, CutoverPlanSourcePort.DeviceSnapshot> expected = new HashMap<>();
        source.devices().forEach(device -> expected.put(device.deviceId(), device));
        List<ObjectNode> rows = new ArrayList<>();
        array.forEach(node -> {
            exact(node, DEVICE, "deviceSummaryItem");
            long id = positiveLong(node, "deviceId");
            CutoverPlanSourcePort.DeviceSnapshot owner = expected.remove(id); require(owner != null, "deviceId");
            require(CutoverPlanRules.comparisonKey(identityText(node, "serialNumber", 128))
                    .equals(CutoverPlanRules.comparisonKey(owner.serialNumber())), "serialNumber");
            require(nonNegativeLong(node, "projectAssignmentVersion") == owner.projectAssignmentVersion(), "projectAssignmentVersion");
            require(text(node, "deviceTypeCode", 64).equals(owner.deviceTypeCode()), "deviceTypeCode");
            require(text(node, "deviceTypeSourceVersion", 128).equals(owner.deviceTypeSourceVersion()), "deviceTypeSourceVersion");
            ObjectNode row = JsonUtils.getObjectMapper().createObjectNode(); putWireLong(row, "deviceId", owner.deviceId());
            row.put("serialNumber", owner.serialNumber()); putWireLong(row, "projectAssignmentVersion", owner.projectAssignmentVersion());
            row.put("deviceTypeCode", owner.deviceTypeCode()); row.put("deviceTypeSourceVersion", owner.deviceTypeSourceVersion()); rows.add(row);
        });
        require(expected.isEmpty(), "deviceSummary"); rows.sort(Comparator.comparingLong(row -> row.path("deviceId").asLong()));
        ArrayNode result = JsonUtils.getObjectMapper().createArrayNode(); rows.forEach(result::add); return result;
    }

    private ArrayNode riskMitigationsDraft(JsonNode array, List<CutoverPlanSourcePort.RiskFactSnapshot> ownerFacts) {
        require(array != null && array.isArray() && array.size() <= ownerFacts.size(), "riskMitigations");
        Map<String, CutoverPlanSourcePort.RiskFactSnapshot> expected = new HashMap<>();
        ownerFacts.forEach(fact -> expected.put(fact.stableItemKey(), fact));
        List<ObjectNode> rows = new ArrayList<>();
        array.forEach(node -> {
            exact(node, MITIGATION, "riskMitigation"); JsonNode fact = node.get("riskFact"); exact(fact, RISK, "riskFact");
            String key = text(fact, "stableItemKey", 128);
            CutoverPlanSourcePort.RiskFactSnapshot owner = expected.remove(key); require(owner != null, "stableItemKey");
            require(positiveLong(fact, "checklistItemId") == owner.checklistItemId(), "checklistItemId");
            require(positiveInt(fact, "itemResultVersion") == owner.itemResultVersion(), "itemResultVersion");
            require(text(fact, "itemName", 255).equals(owner.itemName()), "itemName");
            require(text(fact, "resultCode", 32).equals(owner.resultCode()), "resultCode");
            require(text(fact, "factDescription", 4000).equals(owner.factDescription()), "factDescription");
            ObjectNode frozen = JsonUtils.getObjectMapper().createObjectNode(); putWireLong(frozen, "checklistItemId", owner.checklistItemId());
            frozen.put("stableItemKey", owner.stableItemKey()); frozen.put("itemResultVersion", owner.itemResultVersion());
            frozen.put("itemName", owner.itemName()); frozen.put("resultCode", owner.resultCode());
            frozen.put("factDescription", owner.factDescription());
            ObjectNode row = JsonUtils.getObjectMapper().createObjectNode(); row.set("riskFact", frozen);
            row.put("mitigation", text(node, "mitigation", 4000)); rows.add(row);
        });
        rows.sort(Comparator.comparing(row -> row.path("riskFact").path("stableItemKey").asText()));
        ArrayNode result = JsonUtils.getObjectMapper().createArrayNode(); rows.forEach(result::add); return result;
    }

    private JsonNode nullableFile(JsonNode node) {
        if (node == null || node.isNull()) return JsonUtils.getObjectMapper().nullNode();
        return canonicalFile(node);
    }

    private ObjectNode canonicalFile(JsonNode node) {
        exact(node, FILE, "fileArtifactFact"); exact(node.get("fileFactVersion"), FILE_VERSION, "fileFactVersion");
        ObjectNode result = JsonUtils.getObjectMapper().createObjectNode(); putWireLong(result, "artifactId", positiveLong(node, "artifactId"));
        result.put("versionNo", positiveInt(node, "versionNo")); result.put("referenceKey", text(node, "referenceKey", 128));
        ObjectNode version = JsonUtils.getObjectMapper().createObjectNode(); version.put("artifactVersion", nonNegativeInt(node.get("fileFactVersion"), "artifactVersion"));
        version.put("referenceVersion", nonNegativeInt(node.get("fileFactVersion"), "referenceVersion"));
        version.put("availabilityVersion", nonNegativeInt(node.get("fileFactVersion"), "availabilityVersion"));
        result.set("fileFactVersion", version); putWireLong(result, "scopeVersion", nonNegativeLong(node, "scopeVersion"));
        result.put("sha256", requiredPattern(node, "sha256", "[0-9a-f]{64}")); return result;
    }

    private DecodedContent decodeUpload(JsonNode body) {
        exact(body, UPLOAD, "content");
        require(body.path("ownershipConfirmed").isBoolean() && body.path("ownershipConfirmed").asBoolean(), "ownershipConfirmed");
        JsonNode file = body.get("fileArtifactFact");
        exact(file, FILE, "fileArtifactFact");
        exact(file.get("fileFactVersion"), FILE_VERSION, "fileFactVersion");
        CutoverPlanFilePort.FileFact fact = new CutoverPlanFilePort.FileFact(
                positiveLong(file, "artifactId"), positiveInt(file, "versionNo"), text(file, "referenceKey", 128),
                new CutoverPlanFilePort.FileFactVersion(nonNegativeInt(file.get("fileFactVersion"), "artifactVersion"),
                        nonNegativeInt(file.get("fileFactVersion"), "referenceVersion"),
                        nonNegativeInt(file.get("fileFactVersion"), "availabilityVersion")),
                nonNegativeLong(file, "scopeVersion"), requiredPattern(file, "sha256", "[0-9a-f]{64}"));
        return new DecodedContent(null, List.of(), List.of(), fact, true);
    }

    public JsonNode assembleLegacy(List<PlanStep> sourceSteps) {
        require(sourceSteps != null && !sourceSteps.isEmpty(), "legacy steps");
        Set<String> identities = new HashSet<>();
        List<PlanStep> steps = sourceSteps.stream().peek(step -> {
            require(step != null && CutoverPlanRules.STANDARD_SECTIONS.contains(step.sectionCode()), "legacy sectionCode");
            require(identities.add(step.sectionCode() + ':' + step.stepNo()), "legacy step identity");
        }).sorted(Comparator.comparingInt((PlanStep step) -> SECTION_ORDER.indexOf(step.sectionCode()))
                .thenComparingInt(PlanStep::stepNo)).toList();
        ObjectNode result = JsonUtils.getObjectMapper().createObjectNode();
        result.put("editMode", "LEGACY_READ_ONLY");
        ArrayNode rows = result.putArray("steps");
        steps.forEach(step -> {
            ObjectNode row = rows.addObject(); row.put("sectionCode", step.sectionCode());
            row.put("stepNo", step.stepNo()); row.put("content", step.content());
        });
        return result;
    }

    public void validateComplete(DecodedContent content, CutoverPlanSourcePort.SourceFacts sourceFacts) {
        require(content != null && sourceFacts != null, "complete content");
        if (content.fileFact() != null) {
            require(content.ownershipConfirmed(), "ownershipConfirmed");
            return;
        }
        String mode = content.rootSnapshot().path("editMode").asText();
        if ("ONLINE_TEMPLATE_SIMPLE_D".equals(mode)) {
            require(hasEverySection(content.steps(), CutoverPlanRules.SIMPLE_SECTIONS), "plan sections");
            return;
        }
        require("ONLINE_TEMPLATE_STANDARD".equals(mode), "editMode");
        JsonNode overview = content.rootSnapshot().path("overview");
        require(!overview.path("projectDescription").asText().isBlank()
                && !overview.path("scheduleTable").isEmpty(), "standard overview");
        require(hasEverySection(content.steps(), CutoverPlanRules.STANDARD_SECTIONS), "plan sections");
        require(content.rootSnapshot().path("riskMitigations").size() == sourceFacts.failedRiskFacts().size(),
                "risk mitigations");
        require(content.supportArrangements().size() == CutoverPlanRules.SUPPORT_ROLES.size(),
                "support arrangements");
    }

    private static boolean hasEverySection(List<PlanStep> steps, List<String> sections) {
        return sections.stream().allMatch(section -> steps.stream().anyMatch(step -> step.sectionCode().equals(section)));
    }

    private List<PlanStep> steps(JsonNode array, List<String> allowed) {
        require(array != null && array.isArray(), "steps");
        List<PlanStep> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        array.forEach(node -> {
            exact(node, STEP, "step");
            String section = text(node, "sectionCode", 32);
            require(allowed.contains(section), "sectionCode");
            int no = positiveInt(node, "stepNo");
            require(unique.add(section + ':' + no), "step identity");
            result.add(new PlanStep(section, no, text(node, "content", 4000)));
        });
        return result.stream().sorted(Comparator.comparingInt((PlanStep s) -> SECTION_ORDER.indexOf(s.sectionCode())).thenComparingInt(PlanStep::stepNo)).toList();
    }

    private List<SupportArrangement> supportDraft(JsonNode array) {
        require(array != null && array.isArray() && array.size() <= CutoverPlanRules.SUPPORT_ROLES.size(), "supportArrangements");
        List<SupportArrangement> result = new ArrayList<>();
        array.forEach(node -> {
            exact(node, SUPPORT, "supportArrangement");
            String role = text(node, "roleCode", 32);
            require(CutoverPlanRules.SUPPORT_ROLES.contains(role), "roleCode");
            result.add(new SupportArrangement(nullablePositiveLong(node.get("arrangementId")), role,
                    text(node, "personName", 128), text(node, "dutyDescription", 1000),
                    text(node, "phone", 64), positiveLong(node, "arrivalTime")));
        });
        require(result.stream().map(SupportArrangement::roleCode).collect(java.util.stream.Collectors.toSet()).size()
                == result.size(), "support roles");
        return result.stream().sorted(Comparator.comparingInt(a -> CutoverPlanRules.SUPPORT_ROLES.indexOf(a.roleCode()))).toList();
    }

    private static void exact(JsonNode node, Set<String> keys, String field) {
        require(node != null && node.isObject(), field);
        Set<String> actual = new HashSet<>(); node.properties().forEach(e -> actual.add(e.getKey()));
        require(actual.equals(keys), field + " keys");
    }
    private static String text(JsonNode n, String f, int max) { String v=n.path(f).isTextual()?n.path(f).asText():null; require(v!=null&&!v.isBlank()&&v.equals(v.trim())&&v.length()<=max,f); return v; }
    private static String identityText(JsonNode n, String f, int max) { String v=n.path(f).isTextual()?n.path(f).asText():null; require(v!=null&&!v.trim().isEmpty()&&v.length()<=max,f); return v; }
    private static String draftText(JsonNode n, String f, int max) { String v=n.path(f).isTextual()?n.path(f).asText():null; require(v!=null&&v.equals(v.trim())&&v.length()<=max,f); return v; }
    private static int positiveInt(JsonNode n,String f){int v=n.path(f).isInt()?n.path(f).asInt():0;require(v>0,f);return v;}
    private static int nonNegativeInt(JsonNode n,String f){int v=n.path(f).isInt()?n.path(f).asInt():-1;require(v>=0,f);return v;}
    private static long positiveLong(JsonNode n,String f){long v=wireLong(n.path(f),f);require(v>0,f);return v;}
    private static long nonNegativeLong(JsonNode n,String f){long v=wireLong(n.path(f),f);require(v>=0,f);return v;}
    private static Long nullablePositiveLong(JsonNode n){if(n==null||n.isNull())return null;long v=wireLong(n,"arrangementId");require(v>0,"arrangementId");return v;}
    private static long wireLong(JsonNode node, String field) {
        if (node != null && node.isIntegralNumber()) {
            require(node.canConvertToLong(), field);
            long value = node.asLong();
            require(value > -MAX_SAFE_WIRE_LONG && value < MAX_SAFE_WIRE_LONG, field);
            return value;
        }
        require(node != null && node.isTextual() && node.asText().matches("-?(0|[1-9][0-9]*)"), field);
        try { return Long.parseLong(node.asText()); } catch (NumberFormatException ex) { throw new IllegalArgumentException("invalid " + field, ex); }
    }
    private static void putWireLong(ObjectNode node, String field, long value) {
        if (value > -MAX_SAFE_WIRE_LONG && value < MAX_SAFE_WIRE_LONG) node.put(field, value);
        else node.put(field, Long.toString(value));
    }
    private static String requiredPattern(JsonNode n,String f,String regex){String v=text(n,f,128);require(v.matches(regex),f);return v;}

    public record DecodedContent(JsonNode rootSnapshot, List<PlanStep> steps,
                                 List<SupportArrangement> supportArrangements, CutoverPlanFilePort.FileFact fileFact,
                                 boolean ownershipConfirmed) {}
    public record PlanStep(String sectionCode, int stepNo, String content) {
        public PlanStep { require(CutoverPlanRules.STANDARD_SECTIONS.contains(sectionCode) && stepNo > 0, "planStep"); require(content != null && !content.isBlank() && content.equals(content.trim()) && content.length() <= 4000, "planStep.content"); }
    }
    public record SupportArrangement(Long arrangementId, String roleCode, String personName,
                                     String dutyDescription, String phone, long arrivalTime) {}
}
