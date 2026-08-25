package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.AreaDepartmentMappingRespDTO;

public interface AreaDepartmentMappingService {

    AreaDepartmentMappingRespDTO resolve(String areaCode, String areaLevel);

}
