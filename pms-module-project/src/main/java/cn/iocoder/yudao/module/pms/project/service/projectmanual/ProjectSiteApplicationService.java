package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectSiteDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectSiteMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ProjectSiteCommand;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_LOCATION_SCOPE_INVALID;

/** 项目侧只保存 AST 站点稳定引用、版本及创建时快照。 */
@Service
public class ProjectSiteApplicationService {

    public static final String LOCATION_RESOLVED = "RESOLVED";
    public static final String LOCATION_UNRESOLVED = "UNRESOLVED";
    public static final String SCOPE_ACTIVE = "ACTIVE";

    @Resource
    private AssetLocationApi assetLocationApi;
    @Resource
    private ProjectSiteMapper projectSiteMapper;

    public String validateLocationScope(List<ProjectSiteCommand> sites, String fallbackLocation) {
        if (sites == null || sites.isEmpty()) {
            if (fallbackLocation == null || fallbackLocation.isBlank()) {
                throw exception(PROJECT_LOCATION_SCOPE_INVALID, "未选择站点时必须填写实施地点");
            }
            return LOCATION_UNRESOLVED;
        }
        Set<Long> siteIds = new HashSet<>();
        long primaryCount = sites.stream().filter(site -> Boolean.TRUE.equals(site.primarySite())).count();
        if (primaryCount > 1) {
            throw exception(PROJECT_LOCATION_SCOPE_INVALID, "只能设置一个主站点");
        }
        for (ProjectSiteCommand site : sites) {
            if (site == null || site.siteId() == null || site.siteVersion() == null
                    || !siteIds.add(site.siteId())) {
                throw exception(PROJECT_LOCATION_SCOPE_INVALID, "站点引用、版本不能为空且不能重复");
            }
        }
        assetLocationApi.validateSites(siteIds);
        sites.forEach(site -> assetLocationApi.getSite(site.siteId(), site.siteVersion()));
        return LOCATION_RESOLVED;
    }

    public void bindSites(Long projectId, List<ProjectSiteCommand> sites) {
        if (sites == null || sites.isEmpty()) {
            return;
        }
        boolean implicitPrimary = sites.size() == 1 && sites.stream().noneMatch(s -> Boolean.TRUE.equals(s.primarySite()));
        LocalDateTime now = LocalDateTime.now();
        for (ProjectSiteCommand command : sites) {
            SiteRespDTO site = assetLocationApi.getSite(command.siteId(), command.siteVersion());
            AddressRespDTO address = assetLocationApi.getAddress(site.addressId(), null);
            ProjectSiteDO relation = new ProjectSiteDO();
            relation.setProjectId(projectId);
            relation.setSiteId(site.id());
            relation.setSiteVersionSnapshot(site.version());
            relation.setPrimarySite(implicitPrimary || Boolean.TRUE.equals(command.primarySite()));
            relation.setScopeStatus(SCOPE_ACTIVE);
            relation.setEffectiveFrom(now);
            relation.setSiteCodeSnapshot(site.code());
            relation.setSiteNameSnapshot(site.name());
            relation.setAddressSnapshot(address == null ? null : JsonUtils.toJsonString(address));
            relation.setVersion(0);
            projectSiteMapper.insert(relation);
        }
    }

    public List<ProjectSiteDO> getActiveSites(Long projectId) {
        return projectSiteMapper.selectActiveByProjectId(projectId);
    }
}
