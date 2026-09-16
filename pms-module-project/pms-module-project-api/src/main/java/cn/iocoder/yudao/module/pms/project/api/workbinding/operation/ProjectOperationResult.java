package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import tools.jackson.databind.JsonNode;

/** Business-owned identity; revision identity does not contain a project execution round. */
public record ProjectOperationResult(String ownerContext, String objectType, String objectId,
        String revisionId, Integer objectVersion, String businessFactVersion, String resultCode,
        JsonNode response, boolean replayed) {
    public ProjectOperationResult asReplay() {
        return new ProjectOperationResult(ownerContext, objectType, objectId, revisionId,
                objectVersion, businessFactVersion, resultCode, response, true);
    }
}
