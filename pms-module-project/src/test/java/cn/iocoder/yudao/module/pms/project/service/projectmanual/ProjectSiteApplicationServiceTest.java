package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectSiteDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectSiteMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ProjectSiteCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSiteApplicationServiceTest {

    @Mock private AssetLocationApi assetLocationApi;
    @Mock private ProjectSiteMapper projectSiteMapper;
    @InjectMocks private ProjectSiteApplicationService service;

    @Test
    void fallbackOnlyIsUnresolved() {
        assertEquals("UNRESOLVED", service.validateLocationScope(List.of(), "上海市浦东新区"));
        assertThrows(ServiceException.class, () -> service.validateLocationScope(List.of(), " "));
    }

    @Test
    void multiSiteAllowsOnlyOnePrimaryAndChecksEveryVersion() {
        when(assetLocationApi.getSite(11L, 2)).thenReturn(site(11L, 2));
        when(assetLocationApi.getSite(12L, 3)).thenReturn(site(12L, 3));
        assertEquals("RESOLVED", service.validateLocationScope(List.of(
                new ProjectSiteCommand(11L, 2, true),
                new ProjectSiteCommand(12L, 3, false)), null));

        assertThrows(ServiceException.class, () -> service.validateLocationScope(List.of(
                new ProjectSiteCommand(11L, 2, true),
                new ProjectSiteCommand(12L, 3, true)), null));
    }

    @Test
    void bindTakesSiteAndAddressSnapshotsAndMakesSingleSitePrimary() {
        SiteRespDTO site = site(11L, 2);
        when(assetLocationApi.getSite(11L, 2)).thenReturn(site);
        when(assetLocationApi.getAddress(101L, null)).thenReturn(new AddressRespDTO(
                101L, "CN", "中国", "310000", "上海市", "310100", "上海市",
                "310115", "浦东新区", "世纪大道1号", "中国上海市浦东新区世纪大道1号",
                null, null, 0, 4));

        service.bindSites(900L, List.of(new ProjectSiteCommand(11L, 2, false)));

        ArgumentCaptor<ProjectSiteDO> captor = ArgumentCaptor.forClass(ProjectSiteDO.class);
        verify(projectSiteMapper).insert(captor.capture());
        ProjectSiteDO bound = captor.getValue();
        assertEquals(900L, bound.getProjectId());
        assertEquals(2, bound.getSiteVersionSnapshot());
        assertTrue(bound.getPrimarySite());
        assertTrue(bound.getAddressSnapshot().contains("310115"));
    }

    private SiteRespDTO site(Long id, Integer version) {
        return new SiteRespDTO(id, "SITE-" + id, "站点" + id, 88L, 101L,
                "CUSTOMER_SITE", 0, version);
    }
}
