package cn.iocoder.yudao.module.pms.asset.api.migration;

import java.util.List;
import java.util.Map;

/** AST owns renewal facts; INT owns the source identity, run receipt and binding. */
public interface DeviceWarrantyRenewalMigrationApi {
    List<String> ORIGINAL_COLUMNS = List.of("id", "con_xb", "barcode", "grade", "begin_date",
            "end_date", "warranty", "remark", "isyb", "state");

    record Source(String id, String contractNo, String deviceSn, String grade, String startDate,
                  String endDate, String years, String remark, String extended, String latest) {
        public String sourceKey() {
            return "fb_service:" + id + ":" + (deviceSn == null ? "" : deviceSn);
        }
    }
    record Entry(String sourceKey, Source source, Long targetId, String expectedFingerprint) {}
    record Request(Long tenantId, Long actorId, String owner, List<Entry> entries) {}
    record Result(String sourceKey, Long targetId, String action, Map<String, Object> fields, String fingerprint) {}

    List<Result> preview(Request request);
    List<Result> apply(Request request);
}
