package cn.iocoder.yudao.module.pms.project.service.projectsplit.command;

import java.util.List;

public record ApplyProjectSplitResult(Long requestId, List<CreatedProject> projects,
                                      Long scopeVersion, String changeBatchId, Long treeVersion,
                                      boolean replayed) {
    public record CreatedProject(String clientItemKey, Long projectId, String projectCode) {
    }
}
