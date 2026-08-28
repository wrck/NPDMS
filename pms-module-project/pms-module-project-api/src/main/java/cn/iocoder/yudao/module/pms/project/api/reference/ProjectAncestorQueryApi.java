package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorResult;

public interface ProjectAncestorQueryApi {

    ProjectAncestorResult getAncestors(ProjectAncestorQuery query);
}
