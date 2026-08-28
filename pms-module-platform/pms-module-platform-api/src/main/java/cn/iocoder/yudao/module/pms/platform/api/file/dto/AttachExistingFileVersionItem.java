package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

public record AttachExistingFileVersionItem(
        FileArtifactVersionRevalidationQuery source,
        ExistingFileReferenceTarget target) {

    public AttachExistingFileVersionItem {
        if (source == null || target == null || !FileActionCodes.READ.equals(source.requiredAction())) {
            throw new IllegalArgumentException("invalid existing file attachment item");
        }
        if (source.ownerContext().equals(target.ownerContext())
                && source.objectType().equals(target.objectType())
                && source.objectId().equals(target.objectId())
                && source.purposeCode().equals(target.purposeCode())
                && source.referenceKey().equals(target.referenceKey())) {
            throw new IllegalArgumentException("source and target file references must differ");
        }
    }
}
