package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AreaDepartmentMappingRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo.*;

public interface AssetLocationAdminService {

    PageResult<AddressRespDTO> getAddressPage(AddressPageReqVO reqVO);

    PageResult<SiteRespDTO> getSitePage(SitePageReqVO reqVO);

    PageResult<AreaDepartmentMappingRespDTO> getMappingPage(AreaDepartmentMappingPageReqVO reqVO);

    Long saveMapping(AreaDepartmentMappingSaveReqVO reqVO);

}
