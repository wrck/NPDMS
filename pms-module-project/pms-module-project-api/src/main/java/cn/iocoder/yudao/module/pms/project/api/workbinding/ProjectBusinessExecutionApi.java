package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;

/** Called by business Owners inside their transaction, before locking or writing business records. */
public interface ProjectBusinessExecutionApi {
    /** Owner supplies its own identity; this does not replace its functional permission or data-scope checks. */
    void lockForWrite(WriteRequest request);

    record WriteRequest(Long projectId, String ownerContext, String objectType, ProjectBusinessExecutionSelection selection) { }
}
