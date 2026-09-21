package cn.iocoder.yudao.module.pms.platform.api.file;

import java.util.List;

/** Owner resolves document identity only; this does not grant content access or infer a task. */
public interface FileDocumentSourceProvider {
    record Descriptor(String code, String name) { }
    record Scope(Long projectId, String sourceCode) { }
    List<Descriptor> descriptors();
    Scope resolve(Long tenantId, String ownerContext, String objectType, String objectId, String purposeCode);
}
