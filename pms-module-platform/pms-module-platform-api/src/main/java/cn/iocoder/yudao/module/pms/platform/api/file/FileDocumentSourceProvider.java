package cn.iocoder.yudao.module.pms.platform.api.file;

import java.util.List;

/** Owner resolves document identity only; this does not grant content access or infer a task. */
public interface FileDocumentSourceProvider {
    record Descriptor(String code, String name) { }
    /** 原生业务根与修订来自Owner；分类编码不使用模板要求编码。 */
    record Scope(Long projectId, String sourceCode, String ownerModule, String entityType,
                 Long entityId, Long revisionId) {
        public Scope(Long projectId, String sourceCode) { this(projectId, sourceCode, null, null, null, null); }
        public boolean hasBusinessOwner() { return ownerModule != null && entityType != null && entityId != null; }
    }
    List<Descriptor> descriptors();
    Scope resolve(Long tenantId, String ownerContext, String objectType, String objectId, String purposeCode);
}
