package cn.iocoder.yudao.module.pms.project.api.workbinding.operation;

import java.util.List;

/** Deployment metadata for real Owner application commands, not permission grants or runtime execution. */
public interface ProjectBusinessOperationProvider {
    List<ProjectBusinessOperationDescriptor> operations();
}
