package cn.iocoder.yudao.module.pms.project.domain.template.operation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Optional, versioned operation sub-contract. Its absence preserves the legacy
 * binding interpretation; it never means that an operation is authorized.
 * Invalid field values are retained until validation to produce field errors.
 */
public record TemplateOperationContract(Integer version, List<Operation> operations) {
    public static final int VERSION = 1;

    public TemplateOperationContract {
        operations = operations == null ? null
                : Collections.unmodifiableList(new ArrayList<>(operations));
    }

    public record Operation(String operationCode, Integer operationVersion,
                            Check pre, Check post) { }

    public record Check(String mode, String ruleKey) { }
}
