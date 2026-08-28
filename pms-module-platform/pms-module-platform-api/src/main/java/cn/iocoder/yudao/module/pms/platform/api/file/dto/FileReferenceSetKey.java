package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;

public record FileReferenceSetKey(String ownerContext, String objectType, String objectId, String purposeCode)
        implements Comparable<FileReferenceSetKey> {

    public FileReferenceSetKey {
        ownerContext = FileActionCodes.requireText(ownerContext, "ownerContext");
        objectType = FileActionCodes.requireText(objectType, "objectType");
        objectId = FileActionCodes.requireText(objectId, "objectId");
        purposeCode = FileActionCodes.requireText(purposeCode, "purposeCode");
    }

    @Override
    public int compareTo(FileReferenceSetKey other) {
        int result = ownerContext.compareTo(other.ownerContext);
        if (result == 0) result = objectType.compareTo(other.objectType);
        if (result == 0) result = objectId.compareTo(other.objectId);
        return result == 0 ? purposeCode.compareTo(other.purposeCode) : result;
    }
}
