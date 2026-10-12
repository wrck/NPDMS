package cn.iocoder.yudao.module.pms.asset.api.migration;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.util.List;
/** Limited correction of proven existing legacy records. Baselines are evidence, never business storage. */
public interface DppmsSoftwareHistoryCorrectionApi {
    List<Baseline> preview(DppmsAssetSourceHistoryApi.Command source);
    List<NativeEntityMigrationRow> correct(Correction command);
    record Baseline(String domain, Long sourceId, Long targetId, String fieldsJson) {}
    record Correction(DppmsAssetSourceHistoryApi.Command source, List<Baseline> baselines) {}
}
