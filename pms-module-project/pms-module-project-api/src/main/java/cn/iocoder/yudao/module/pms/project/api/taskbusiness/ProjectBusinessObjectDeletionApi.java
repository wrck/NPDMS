package cn.iocoder.yudao.module.pms.project.api.taskbusiness;

/** Internal Owner callback, after an authorized delete, in the same transaction. */
public interface ProjectBusinessObjectDeletionApi {
    /** Close live references only; completed execution evidence and link history remain immutable. */
    void unlinkDeletedObject(DeletedObject object);

    record DeletedObject(Long projectId, String ownerContext, String objectType, String objectId) { }
}
