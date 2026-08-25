package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationInput;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteLocationDO;

import java.util.List;

public interface SiteLocationTreeService {

    SiteLocationDO maintain(Long siteId, SiteLocationInput input);

    SiteLocationDO get(Long locationId, Integer expectedVersion);

    List<SiteLocationDO> getTree(Long siteId);

    void disable(Long locationId, Integer expectedVersion);

}
