package cn.iocoder.yudao.module.pms.project.api.migration;

import java.util.List;
import java.util.Map;

/** Related parties and declared project status attributes, through the normal project Owner. */
public interface ProjectFactsMigrationApi {
    List<String> PARTY_COLUMNS=List.of("id","projectId","partyRole","partyCode","partyName","createTime","createBy",
            "updateTime","updateBy","effectiveFrom","effectiveTo");
    List<String> STATE_COLUMNS=List.of("projectId","projectPlanState","projectplanTime","shipmentState","shipmentTime",
            "executionState","executionStateTime","closeProcessState","closeProcessStateTime");
    record Entry(String object, String sourceKey, Map<String,String> originalFields, String creator,
                 String updater, Long targetId, String expectedFingerprint) {}
    record Command(ProjectMigrationIdentityContextApi.Query identity, Long extensionDefinitionId, List<Entry> entries) {}
    record Result(String object, String sourceKey, Long targetId, String action, Map<String,Object> fields, String fingerprint) {}
    List<Result> preview(Command command);
    List<Result> apply(Command command);
    record DefinitionRequest(Long tenantId, Long actorId, Long existingRevisionId) {}
    Long configureDefinition(DefinitionRequest request);
}
