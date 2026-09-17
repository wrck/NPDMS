package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOwnerOperationScope;

/** Called by business Owners inside their transaction, before locking or writing business records. */
public interface ProjectBusinessExecutionApi {
    /** Owner supplies its own identity; this does not replace its functional permission or data-scope checks. */
    void lockForWrite(WriteRequest request);

    record WriteRequest(Long projectId, String ownerContext, String objectType, ProjectBusinessExecutionSelection selection,
                        String operationCode, Integer operationVersion, String objectId,
                        ProjectOwnerOperationScope.Proof ownerProof) {
        /** Direct operation checks retain their exact subject identity; no callback proof is implied. */
        public WriteRequest(Long projectId, String ownerContext, String objectType, ProjectBusinessExecutionSelection selection,
                            String operationCode, Integer operationVersion, String objectId) {
            this(projectId, ownerContext, objectType, selection, operationCode, operationVersion, objectId, null);
        }
        /** Legacy callers have no exact operation identity and cannot borrow a controlled invocation's checks. */
        public WriteRequest(Long projectId, String ownerContext, String objectType, ProjectBusinessExecutionSelection selection) {
            this(projectId, ownerContext, objectType, selection, null, null, null);
        }
    }
}
