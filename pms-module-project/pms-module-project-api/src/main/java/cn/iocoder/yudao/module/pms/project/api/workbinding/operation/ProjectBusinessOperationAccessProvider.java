package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import java.util.Set;

/** Owner-authorized read only. No execution node, view registration or task status is required. */
public interface ProjectBusinessOperationAccessProvider {
    String ownerContext();
    String objectType();
    Access inspect(Context context);

    record Context(Long tenantId, Long actorId, Long projectId, String objectId) { }
    record Access(Set<String> permittedOperations, String objectFactVersion) {
        public Access { permittedOperations = Set.copyOf(permittedOperations); }
    }
}
