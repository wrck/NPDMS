package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProjectCodeQueryApiImpl implements ProjectCodeQueryApi {

    private final ProjectMasterMapper projectMapper;

    @Override
    public String getProjectCode(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("项目编号不能为空");
        }
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId())) {
            throw new IllegalArgumentException("PROJECT_NOT_FOUND");
        }
        if (project.getProjectCode() == null || project.getProjectCode().isBlank()) {
            throw new IllegalArgumentException("PROJECT_CODE_MISSING");
        }
        return project.getProjectCode();
    }
}
