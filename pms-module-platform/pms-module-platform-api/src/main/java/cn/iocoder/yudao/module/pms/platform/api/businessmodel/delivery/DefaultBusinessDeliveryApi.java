package cn.iocoder.yudao.module.pms.platform.api.businessmodel.delivery;

import java.time.LocalDateTime;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;

/** Four-field delivery identity shared by the default business implementation. */
public interface DefaultBusinessDeliveryApi {
    record Scope(Long projectId, String businessType, String businessEntityKey, String deliverableType) { }
    record Record(String id, Long projectId, String businessType, String businessEntityKey,
                  String deliverableType, String title, String fileName, String fileReferenceId,
                  String fileArtifactId, Integer fileVersionNo, LocalDateTime uploadedAt, Long version,
                  String status, String ownerModule, String entityType, String materialKind,
                  String sourceKind, boolean editable) { }
    /** Transport-neutral upload; bytes remain subject to the common bounded content validator. */
    record UploadFile(String name, String mediaType, long size,
                      java.util.function.Supplier<java.io.InputStream> content) { }
    record Completion(boolean completed, Record latest) { }
    Scope context(String ownerModule, String entityType, String entityKey);
    Record upload(Scope scope, UploadFile file, String requestKey);
    PageResult<Record> list(Long projectId, String deliverableType, String businessType,
                            String businessEntityKey, int pageNo, int pageSize);
    Record get(Long id);
    FileEvidenceApi.Document file(Long id);
    Record edit(Long id, Long version, String title);
    void delete(Long id, Long version);
    Completion completion(Scope scope);
}
