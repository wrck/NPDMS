package cn.iocoder.yudao.module.pms.project.domain.projectsplit;

import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitDraftCommand;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class ProjectSplitRules {

    public List<String> validate(ProjectSplitDraftCommand command) {
        List<String> errors = new ArrayList<>();
        if (command == null || command.parentProjectId() == null || command.items() == null
                || command.items().isEmpty()) {
            return List.of("INVALID_DRAFT");
        }
        Set<String> itemKeys = new HashSet<>();
        Set<String> serials = new HashSet<>();
        for (ProjectSplitDraftCommand.Item item : command.items()) {
            if (item == null || item.clientItemKey() == null || item.clientItemKey().isBlank()
                    || item.clientItemKey().length() > 64 || item.projectName() == null
                    || item.projectName().isBlank() || item.projectName().length() > 255
                    || item.scopes() == null || item.scopes().isEmpty()) {
                errors.add("INVALID_ITEM");
                continue;
            }
            if (!itemKeys.add(item.clientItemKey())) {
                errors.add("DUPLICATE_ITEM_KEY:" + item.clientItemKey());
            }
            for (ProjectSplitDraftCommand.Scope scope : item.scopes()) {
                if (scope == null || scope.orderLineId() == null || scope.quantity() == null
                        || scope.quantity().signum() <= 0) {
                    errors.add("INVALID_SCOPE:" + item.clientItemKey());
                    continue;
                }
                List<String> normalized = scope.serialNumbers() == null ? List.of() : scope.serialNumbers().stream()
                        .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).toList();
                if (!normalized.isEmpty() && scope.quantity().compareTo(java.math.BigDecimal.valueOf(normalized.size())) != 0) {
                    errors.add("SERIAL_QUANTITY_MISMATCH:" + item.clientItemKey());
                }
                for (String serial : normalized) {
                    if (serial.length() > 128 || !serials.add(serial)) {
                        errors.add("DUPLICATE_OR_INVALID_SERIAL:" + serial);
                    }
                }
            }
        }
        return List.copyOf(errors);
    }
}
