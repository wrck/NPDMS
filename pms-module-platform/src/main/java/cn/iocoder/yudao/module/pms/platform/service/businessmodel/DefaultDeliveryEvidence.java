package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import java.util.Objects;

/** The same valid-upload predicate is used by the public completion API and template runtime. */
public final class DefaultDeliveryEvidence {
    private DefaultDeliveryEvidence() { }
    public static boolean available(DeliveryMaterialDO row, FileEvidenceApi.Document document) {
        return row != null && !Boolean.TRUE.equals(row.getDeleted())
                && DeliveryMaterialDO.STATUS_ACTIVE.equals(row.getStatus())
                && document != null && document.available()
                && "PLT".equals(document.ownerContext())
                && DefaultBusinessDeliveryService.FILE_OBJECT_TYPE.equals(document.objectType())
                && Objects.equals(row.getFileReferenceId(), document.referenceId())
                && Objects.equals(row.getFileArtifactId(), document.artifactId())
                && Objects.equals(row.getFileVersionNo(), document.versionNo())
                && row.getFileSha256() != null && row.getFileSha256().equals(document.sha256())
                && Objects.equals(row.getTypeCode(), document.purposeCode())
                && (row.getProjectId()+":"+row.getOwnerModule()+":"+row.getEntityType()+":"+row.getEntityId()).equals(document.objectId());
    }
}
