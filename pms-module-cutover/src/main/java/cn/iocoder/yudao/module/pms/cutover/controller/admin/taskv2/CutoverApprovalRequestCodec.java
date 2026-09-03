package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.approval.CutoverApprovalRequests;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.AssessmentReviewInput;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.ReviewItemInput;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** F-CUT-005 六路由请求边界；只解析wire/schema，不判断审批状态。 */
public final class CutoverApprovalRequestCodec {
    private static final long JS_SAFE_INTEGER_BOUNDARY = 9_007_199_254_740_991L;
    private static final List<String> REVIEW_ORDER = List.of(
            "PREPARATION", "BUSINESS_TEST", "EXECUTION", "ROLLBACK", "OTHER");
    private static final Set<String> DECISION = Set.of("action", "reviewItems", "assessmentReview", "feedback");
    private static final Set<String> REVIEW = Set.of("itemCode", "decision", "unreasonableReason");
    private static final Set<String> ASSESSMENT = Set.of("decision", "reason");
    private static final Set<String> REASSIGN = Set.of("nodeNo", "newApproverUserId", "reason");

    public CutoverApprovalRequests.Decision approve(JsonNode body) {
        return decision(body, "APPROVE");
    }

    public CutoverApprovalRequests.Decision reject(JsonNode body) {
        return decision(body, "REJECT");
    }

    public CutoverApprovalRequests.Reassign reassign(JsonNode body) {
        exact(body, REASSIGN, "reassign");
        return new CutoverApprovalRequests.Reassign(positiveInt(body.get("nodeNo"), "nodeNo"),
                wireLong(body.get("newApproverUserId"), "newApproverUserId"),
                text(body.get("reason"), "reason", 1000));
    }

    public int version(String value, String name) {
        String text = header(value, name);
        try {
            int result = Integer.parseInt(text);
            if (result < 0) throw invalidHeader(name);
            return result;
        } catch (NumberFormatException ex) {
            throw invalidHeader(name);
        }
    }

    public String header(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 128) {
            throw invalidHeader(name);
        }
        return value;
    }

    private CutoverApprovalRequests.Decision decision(JsonNode body, String expectedAction) {
        exact(body, DECISION, "decision");
        if (!expectedAction.equals(text(body.get("action"), "action", 16))) throw invalid("action");
        JsonNode itemsNode = body.get("reviewItems");
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.size() != REVIEW_ORDER.size()) {
            throw invalid("reviewItems");
        }
        List<ReviewItemInput> items = new ArrayList<>();
        for (int index = 0; index < REVIEW_ORDER.size(); index++) {
            JsonNode item = itemsNode.get(index);
            exact(item, REVIEW, "reviewItem");
            String code = text(item.get("itemCode"), "itemCode", 32);
            if (!REVIEW_ORDER.get(index).equals(code)) throw invalid("itemCode");
            String decision = text(item.get("decision"), "decision", 3);
            if (!List.of("YES", "NO").contains(decision)) throw invalid("decision");
            String reason = nullableText(item.get("unreasonableReason"), "unreasonableReason", 1000);
            if (("YES".equals(decision) && reason != null) || ("NO".equals(decision) && reason == null)) {
                throw invalid("unreasonableReason");
            }
            if ("APPROVE".equals(expectedAction) && !"YES".equals(decision)) throw invalid("decision");
            items.add(new ReviewItemInput(code, decision, reason));
        }
        AssessmentReviewInput assessment = assessment(body.get("assessmentReview"));
        if ("REJECT".equals(expectedAction)
                && items.stream().noneMatch(item -> "NO".equals(item.decision()))
                && (assessment == null || !"NOT_REASONABLE".equals(assessment.decision()))) {
            throw invalid("actionResult");
        }
        return new CutoverApprovalRequests.Decision(List.copyOf(items), assessment,
                text(body.get("feedback"), "feedback", 1000));
    }

    private AssessmentReviewInput assessment(JsonNode node) {
        if (node == null || node.isNull()) return null;
        exact(node, ASSESSMENT, "assessmentReview");
        String decision = text(node.get("decision"), "assessmentDecision", 32);
        if (!List.of("CONFIRMED", "NOT_REASONABLE").contains(decision)) throw invalid("assessmentDecision");
        String reason = nullableText(node.get("reason"), "assessmentReason", 1000);
        if (("CONFIRMED".equals(decision) && reason != null)
                || ("NOT_REASONABLE".equals(decision) && reason == null)) throw invalid("assessmentReason");
        return new AssessmentReviewInput(decision, reason);
    }

    private static int positiveInt(JsonNode node, String field) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() <= 0) {
            throw invalid(field);
        }
        return node.asInt();
    }

    private static long wireLong(JsonNode node, String field) {
        if (node == null || !(node.isIntegralNumber() || node.isTextual())) throw invalid(field);
        try {
            long value;
            if (node.isTextual()) {
                String text = node.asText();
                if (!text.matches("[1-9][0-9]*")) throw invalid(field);
                value = Long.parseLong(text);
            } else {
                if (!node.canConvertToLong()) throw invalid(field);
                value = node.asLong();
                if (value <= -JS_SAFE_INTEGER_BOUNDARY || value >= JS_SAFE_INTEGER_BOUNDARY) throw invalid(field);
            }
            if (value <= 0) throw invalid(field);
            return value;
        } catch (NumberFormatException ex) {
            throw invalid(field);
        }
    }

    private static String text(JsonNode node, String field, int max) {
        String value = nullableText(node, field, max);
        if (value == null) throw invalid(field);
        return value;
    }

    private static String nullableText(JsonNode node, String field, int max) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw invalid(field);
        String value = node.asText();
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > max) throw invalid(field);
        return value;
    }

    private static void exact(JsonNode node, Set<String> keys, String field) {
        if (node == null || !node.isObject()) throw invalid(field);
        if (!new HashSet<>(node.propertyNames()).equals(keys)) throw invalid(field);
    }

    private static CutoverApprovalRequestException invalid(String field) {
        return new CutoverApprovalRequestException(CutoverApprovalRequestException.Reason.REQUEST_SCHEMA_INVALID,
                "invalid " + field);
    }

    private static CutoverApprovalRequestException invalidHeader(String field) {
        return new CutoverApprovalRequestException(CutoverApprovalRequestException.Reason.HEADER_REQUIRED_OR_INVALID,
                "invalid " + field);
    }
}
