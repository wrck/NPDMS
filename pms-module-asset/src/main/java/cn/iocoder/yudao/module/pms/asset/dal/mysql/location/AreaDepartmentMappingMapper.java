package cn.iocoder.yudao.module.pms.asset.dal.mysql.location;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo.AreaDepartmentMappingPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.AreaDepartmentMappingDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.time.LocalDateTime;

@Mapper
public interface AreaDepartmentMappingMapper extends BaseMapperX<AreaDepartmentMappingDO> {

    default PageResult<AreaDepartmentMappingDO> selectPage(AreaDepartmentMappingPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AreaDepartmentMappingDO>()
                .eqIfPresent(AreaDepartmentMappingDO::getAreaCode, reqVO.getAreaCode())
                .eqIfPresent(AreaDepartmentMappingDO::getAreaLevel, reqVO.getAreaLevel())
                .eqIfPresent(AreaDepartmentMappingDO::getDepartmentCode, reqVO.getDepartmentCode())
                .eqIfPresent(AreaDepartmentMappingDO::getStatus, reqVO.getStatus())
                .orderByDesc(AreaDepartmentMappingDO::getId));
    }

    default java.util.List<AreaDepartmentMappingDO> selectListByArea(String areaCode, String areaLevel,
                                                                      String mappingType) {
        return selectList(new LambdaQueryWrapperX<AreaDepartmentMappingDO>()
                .eq(AreaDepartmentMappingDO::getAreaCode, areaCode)
                .eq(AreaDepartmentMappingDO::getAreaLevel, areaLevel)
                .eq(AreaDepartmentMappingDO::getMappingType, mappingType));
    }

    default AreaDepartmentMappingDO selectCurrent(String areaCode, String areaLevel, String mappingType,
                                                   Integer status, LocalDateTime currentTime) {
        return selectOne(new LambdaQueryWrapperX<AreaDepartmentMappingDO>()
                .eq(AreaDepartmentMappingDO::getAreaCode, areaCode)
                .eq(AreaDepartmentMappingDO::getAreaLevel, areaLevel)
                .eq(AreaDepartmentMappingDO::getMappingType, mappingType)
                .eq(AreaDepartmentMappingDO::getStatus, status)
                .le(AreaDepartmentMappingDO::getEffectiveFrom, currentTime)
                .and(wrapper -> wrapper.isNull(AreaDepartmentMappingDO::getEffectiveTo)
                        .or().gt(AreaDepartmentMappingDO::getEffectiveTo, currentTime)));
    }

    default int updateByIdAndVersion(AreaDepartmentMappingDO update, Integer expectedVersion) {
        return update(update, new LambdaUpdateWrapper<AreaDepartmentMappingDO>()
                .eq(AreaDepartmentMappingDO::getId, update.getId())
                .eq(AreaDepartmentMappingDO::getVersion, expectedVersion));
    }
}
