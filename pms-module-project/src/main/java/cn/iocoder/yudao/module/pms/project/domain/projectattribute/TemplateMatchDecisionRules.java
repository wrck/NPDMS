package cn.iocoder.yudao.module.pms.project.domain.projectattribute;

import java.util.Set;

/** PM-07四属性、触发矩阵和影响结论的纯领域规则。 */
public final class TemplateMatchDecisionRules {

    public static final String SNAPSHOT_SCHEMA_VERSION = "1";
    public static final String MATCHER_VERSION = "TEMPLATE_MATCHER_V1";
    public static final String MATCH_UNIQUE = "UNIQUE";
    public static final String MATCH_NO_MATCH = "NO_MATCH";
    public static final String MATCH_MULTIPLE = "MULTIPLE_MATCHES";
    public static final String DECISION_AUTO_UNIQUE = "AUTO_UNIQUE";
    public static final String DECISION_EXPLICIT = "EXPLICIT_SELECTION";
    public static final String TRIGGER_INITIAL = "INITIAL_CREATE";
    public static final String TRIGGER_SOURCE = "SOURCE_CORRECTION";
    public static final String TRIGGER_MANUAL = "MANUAL_ADJUSTMENT";
    public static final String PURPOSE_CREATE = "CREATE_DECISION";
    public static final String PURPOSE_IMPACT = "IMPACT_EVALUATION";
    public static final String IMPACT_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String IMPACT_NONE = "NO_IMPACT";
    public static final String IMPACT_CHANGED = "CANDIDATE_CHANGED";

    private static final Set<String> PROJECT_CATEGORIES = Set.of("GENERAL", "ENGINEERING");

    private TemplateMatchDecisionRules() {
    }

    public static ProjectAttributeSnapshot requireManualCreationAttributes(ProjectAttributeSnapshot snapshot) {
        ProjectAttributeSnapshot normalized = requireCommonAttributes(snapshot);
        if (snapshot.majorProjectLevel() != null && !snapshot.majorProjectLevel().isBlank()) {
            throw new IllegalArgumentException("手工项目不得填写CRM重大项目级别");
        }
        return new ProjectAttributeSnapshot(normalized.signingMethod(), normalized.projectCategory(),
                normalized.implementationMode(), null);
    }

    public static ProjectAttributeSnapshot requireCommonAttributes(ProjectAttributeSnapshot snapshot) {
        if (snapshot == null || isBlank(snapshot.signingMethod()) || isBlank(snapshot.projectCategory())
                || isBlank(snapshot.implementationMode())) {
            throw new IllegalArgumentException("模板匹配所需签约方式、项目类别和实施方式不能为空");
        }
        if (!PROJECT_CATEGORIES.contains(trim(snapshot.projectCategory()))) {
            throw new IllegalArgumentException("项目类别只允许GENERAL或ENGINEERING");
        }
        return new ProjectAttributeSnapshot(trim(snapshot.signingMethod()), trim(snapshot.projectCategory()),
                trim(snapshot.implementationMode()), blankToNull(snapshot.majorProjectLevel()));
    }

    public static ProjectAttributeOwnerSnapshot requireOwners(ProjectAttributeOwnerSnapshot owners) {
        if (owners == null || isBlank(owners.signingMethodOwner()) || isBlank(owners.projectCategoryOwner())
                || isBlank(owners.implementationModeOwner()) || isBlank(owners.majorProjectLevelOwner())) {
            throw new IllegalArgumentException("四属性Owner快照不完整");
        }
        return owners;
    }

    public static String requireReason(String reason) {
        if (isBlank(reason)) {
            throw new IllegalArgumentException("变更原因不能为空");
        }
        return reason.trim();
    }

    public static String impactResult(TemplateMatchDecision decision, Long frozenTemplateRevisionId) {
        if (decision == null || frozenTemplateRevisionId == null) {
            throw new IllegalArgumentException("影响评估缺少匹配决策或冻结模板修订");
        }
        return switch (decision.matchResult()) {
            case MATCH_UNIQUE -> frozenTemplateRevisionId.equals(decision.matchedTemplateRevisionId())
                    ? IMPACT_NONE : IMPACT_CHANGED;
            case MATCH_NO_MATCH -> MATCH_NO_MATCH;
            case MATCH_MULTIPLE -> MATCH_MULTIPLE;
            default -> throw new IllegalArgumentException("未知模板匹配结果");
        };
    }

    public static void validateInitialDecision(TemplateMatchDecision decision) {
        requireDecisionEvidence(decision);
        if (decision.decisionMode() == null
                || !Set.of(DECISION_AUTO_UNIQUE, DECISION_EXPLICIT).contains(decision.decisionMode())
                || decision.matchedTemplateId() == null || decision.matchedTemplateRevisionId() == null
                || MATCH_NO_MATCH.equals(decision.matchResult())) {
            throw new IllegalArgumentException("首次创建决策不满足持久化矩阵");
        }
        if (MATCH_MULTIPLE.equals(decision.matchResult()) && !DECISION_EXPLICIT.equals(decision.decisionMode())) {
            throw new IllegalArgumentException("多匹配只能持久化合法显式选择");
        }
    }

    public static void validateImpactDecision(TemplateMatchDecision decision) {
        requireDecisionEvidence(decision);
        if (decision.decisionMode() != null) {
            throw new IllegalArgumentException("创建后影响评估不得保存决策方式");
        }
        boolean unique = MATCH_UNIQUE.equals(decision.matchResult());
        boolean bothMatched = decision.matchedTemplateId() != null && decision.matchedTemplateRevisionId() != null;
        boolean bothEmpty = decision.matchedTemplateId() == null && decision.matchedTemplateRevisionId() == null;
        if ((unique && !bothMatched) || (!unique && !bothEmpty)) {
            throw new IllegalArgumentException("影响评估命中模板字段不满足结果矩阵");
        }
    }

    private static void requireDecisionEvidence(TemplateMatchDecision decision) {
        if (decision == null || isBlank(decision.candidateDigest()) || isBlank(decision.matcherVersion())
                || !Set.of(MATCH_UNIQUE, MATCH_NO_MATCH, MATCH_MULTIPLE).contains(decision.matchResult())) {
            throw new IllegalArgumentException("模板匹配决策证据不完整");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
