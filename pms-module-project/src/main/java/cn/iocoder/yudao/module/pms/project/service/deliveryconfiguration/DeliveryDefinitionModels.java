package cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration;

import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import tools.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.List;

/** PM-03: configuration DTOs; no trusted tenant/actor accepted in bodies. */
public final class DeliveryDefinitionModels {
    private DeliveryDefinitionModels() { }
    public record Save(DeliveryDefinitionKind definitionKind, String definitionCode, Integer schemaVersion,
                       JsonNode payload, List<DeliveryDefinitionReference> references) { }
    public record Revision(Long id, DeliveryDefinitionKind definitionKind, String definitionCode, Long revisionNo,
                           String revisionState, Integer schemaVersion, JsonNode payload,
                           List<DeliveryDefinitionReference> references, LocalDateTime publishedAt,
                           LocalDateTime disabledAt, Integer version) { }
    public record Snapshot(Revision definition, BusinessViewRevision businessView) { }
    public record Issue(String field, String code, String message) { }
    public record Validation(boolean valid, List<Issue> issues) {
        public static Validation of(List<Issue> issues) { return new Validation(issues.isEmpty(), List.copyOf(issues)); }
    }
}
