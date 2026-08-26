package cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentPageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.query.CustomerDeviceReferenceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.query.CustomerDeviceSummaryPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EquipmentMapper extends BaseMapperX<EquipmentDO> {

    default PageResult<EquipmentDO> selectPage(EquipmentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EquipmentDO>()
                .eqIfPresent(EquipmentDO::getSerialNumber, reqVO.getSerialNumber())
                .likeIfPresent(EquipmentDO::getName, reqVO.getName())
                .eqIfPresent(EquipmentDO::getModel, reqVO.getModel())
                .eqIfPresent(EquipmentDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(EquipmentDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(EquipmentDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(EquipmentDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(EquipmentDO::getId));
    }

    default Long selectCountByCustomer(CustomerDeviceReferenceQuery query) {
        return selectCount(new LambdaQueryWrapperX<EquipmentDO>()
                .eq(EquipmentDO::getTenantId, query.tenantId())
                .eq(EquipmentDO::getCustomerId, query.customerId()));
    }

    default PageResult<EquipmentDO> selectCustomerSummaryPage(CustomerDeviceSummaryPageQuery query) {
        return selectPage(query, new LambdaQueryWrapperX<EquipmentDO>()
                .eq(EquipmentDO::getTenantId, query.getTenantId())
                .eq(EquipmentDO::getCustomerId, query.getCustomerId())
                .orderByDesc(EquipmentDO::getId));
    }

    default EquipmentDO selectBySerialNumber(String serialNumber) {
        return selectOne(EquipmentDO::getSerialNumber, serialNumber);
    }

    default List<EquipmentDO> selectListBySerialNumbers(Collection<String> serialNumbers) {
        return selectList(new LambdaQueryWrapperX<EquipmentDO>()
                .in(EquipmentDO::getSerialNumber, serialNumbers));
    }

    default Long selectCountBySiteLocationId(Long siteLocationId) {
        return selectCount(new LambdaQueryWrapperX<EquipmentDO>()
                .eq(EquipmentDO::getSiteLocationId, siteLocationId));
    }

    @Update("""
            UPDATE pms_equipment
            SET site_id = #{update.siteId},
                site_location_id = #{update.siteLocationId},
                location = #{update.location},
                location_resolution_status = #{update.locationResolutionStatus},
                location_snapshot = #{update.locationSnapshot},
                location_effective_from = #{update.locationEffectiveFrom},
                location_source_installation_id = #{update.locationSourceInstallationId},
                version = version + 1
            WHERE id = #{update.id}
              AND version = #{expectedVersion}
              AND deleted = b'0'
            """)
    int updateLocationIfMatch(@Param("update") EquipmentDO update,
                              @Param("expectedVersion") Integer expectedVersion);

}
