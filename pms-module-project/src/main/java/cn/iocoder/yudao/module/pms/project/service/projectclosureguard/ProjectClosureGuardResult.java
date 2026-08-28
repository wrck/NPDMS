package cn.iocoder.yudao.module.pms.project.service.projectclosureguard;

import java.util.List;

public record ProjectClosureGuardResult(boolean allowed, long treeVersion,
                                        List<BlockingProject> blockers,
                                        List<Long> pendingProgressProjects) {

    public record BlockingProject(Long projectId, String projectCode, String projectName,
                                  String blockerType) {}
}
