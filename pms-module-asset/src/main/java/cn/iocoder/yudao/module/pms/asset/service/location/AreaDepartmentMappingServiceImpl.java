package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AreaDepartmentMappingRespDTO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.AreaDepartmentMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.AreaDepartmentMappingMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AreaDepartmentMappingServiceImpl implements AreaDepartmentMappingService {

    public static final String SERVICE_OFFICE = "SERVICE_OFFICE";

    private final AreaDepartmentMappingMapper mappingMapper;
    private final DeptApi deptApi;

    @Override
    public AreaDepartmentMappingRespDTO resolve(String areaCode, String areaLevel) {
        if (areaCode == null || areaLevel == null) {
            return null;
        }
        AreaDepartmentMappingDO mapping = mappingMapper.selectCurrent(areaCode, areaLevel, SERVICE_OFFICE,
                CommonStatusEnum.ENABLE.getStatus(), LocalDateTime.now());
        if (mapping == null) {
            return null;
        }
        DeptRespDTO department;
        try {
            department = deptApi.getDeptByCode(mapping.getDepartmentCode());
        } catch (ServiceException ignored) {
            return null;
        }
        if (department == null || !CommonStatusEnum.isEnable(department.getStatus())) {
            return null;
        }
        return new AreaDepartmentMappingRespDTO(mapping.getId(), mapping.getAreaCode(), mapping.getAreaLevel(),
                mapping.getMappingType(), mapping.getDepartmentCode(), department.getName(),
                mapping.getEffectiveFrom(), mapping.getEffectiveTo(), mapping.getVersion());
    }

}
