package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.module.pms.service.domain.inspectionrule.InspectionRuleRevisionRules;

import java.math.BigDecimal;
import java.util.List;

public interface InspectionRuleRevisionService {

    DraftResult createDraft(CreateDraftCommand command);

    DraftResult saveDraft(SaveDraftCommand command);

    DraftResult copyRevision(Long sourceRevisionId);

    ValidationResult validateRevision(Long revisionId);

    record CreateDraftCommand(String detectionId, String ruleName) {
    }

    record SaveDraftCommand(
            Long revisionId,
            Integer expectedVersion,
            String inspectionItem,
            String description,
            String categoryCode,
            String categoryNameSnapshot,
            String severityCode,
            String severityNameSnapshot,
            Integer sortOrder,
            String expectedResultRegex,
            String thresholdDataType,
            String thresholdOperator,
            BigDecimal thresholdValue,
            String thresholdUnit,
            List<CommandDraft> commands,
            List<ProductTypeDraft> productTypes) {

        public SaveDraftCommand {
            commands = commands == null ? List.of() : List.copyOf(commands);
            productTypes = productTypes == null ? List.of() : List.copyOf(productTypes);
        }
    }

    record CommandDraft(
            String stableCommandKey,
            String commandContent,
            Integer executionOrder,
            Integer timeoutSeconds,
            Boolean continueOnTimeout) {
    }

    record ProductTypeDraft(String productTypeCode, String productTypeNameSnapshot) {
    }

    record DraftResult(Long ruleId, Long revisionId, Integer revisionNo, Integer version) {
    }

    record DictionaryNameCandidate(String field, String value, String authoritativeDisplayName) {
    }

    record ProductTypeNameCandidate(String productTypeCode, String authoritativeDisplayName) {
    }

    record ValidationResult(
            List<InspectionRuleRevisionRules.ValidationError> errors,
            List<DictionaryNameCandidate> dictionaryNameCandidates,
            List<ProductTypeNameCandidate> productTypeNameCandidates) {

        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
            dictionaryNameCandidates = dictionaryNameCandidates == null
                    ? List.of() : List.copyOf(dictionaryNameCandidates);
            productTypeNameCandidates = productTypeNameCandidates == null
                    ? List.of() : List.copyOf(productTypeNameCandidates);
        }
    }
}
