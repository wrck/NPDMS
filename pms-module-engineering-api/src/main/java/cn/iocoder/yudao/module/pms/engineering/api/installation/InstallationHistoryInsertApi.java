package cn.iocoder.yudao.module.pms.engineering.api.installation;

import java.time.LocalDateTime;
import java.util.List;

/** First insertion reuses the approved installation carry rules and formal address/location Owners. */
public interface InstallationHistoryInsertApi {
    List<Result> preview(Command command);
    List<Result> insert(Command command);
    /** Read and compare already bound rows; never rewrites immutable historical records. */
    List<Result> verify(Verification command);
    record Verification(Long tenantId,Long actorId,Long projectId,List<Existing> rows) {}
    record Existing(String sourceKey,Long installationId,Long equipmentId,String formalFieldsJson) {}
    /** All unbound history for the selected device from one complete source read; never an arbitrary source chunk. */
    record Command(Long tenantId,Long actorId,Long projectId,List<Row> rows) {}
    record Row(String sourceKey,Long equipmentId,java.util.Map<String,String> originalFields) {}
    record Result(String sourceKey,Long installationId,String code,LocalDateTime effectiveTo,String formalFieldsJson) {}
}
