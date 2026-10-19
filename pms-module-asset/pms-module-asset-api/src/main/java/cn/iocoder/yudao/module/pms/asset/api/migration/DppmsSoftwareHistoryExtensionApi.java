package cn.iocoder.yudao.module.pms.asset.api.migration;
import java.util.Map;
/** Shared extension initialization only; no software record or definition publishing writes. */
public interface DppmsSoftwareHistoryExtensionApi {
    record Initialization(Long tenantId,Long actorId,Long recordId,Long definitionId,Map<String,Object> values) {}
    void preview(Initialization command);
    void initialize(Initialization command);
}
