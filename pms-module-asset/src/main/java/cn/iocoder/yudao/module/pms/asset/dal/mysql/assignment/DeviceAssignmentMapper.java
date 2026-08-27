package cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceAncestorProjectionOperationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceAssignmentReconciliationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectAncestorDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAncestorProjectionWatermarkQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceCustomerAssignmentUpdate;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceCustomerRelationshipPageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceProjectAssignmentUpdate;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceProjectHistoryPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DeviceAssignmentMapper {

    DeviceDO selectDeviceForUpdate(@Param("query") DeviceAssignmentLockQuery query);

    DeviceProjectRelationshipDO selectCurrentProject(@Param("query") DeviceAssignmentLockQuery query);

    int closeCurrentProject(@Param("tenantId") Long tenantId,
                            @Param("deviceId") Long deviceId,
                            @Param("effectiveTo") LocalDateTime effectiveTo,
                            @Param("assignmentVersion") Long assignmentVersion);

    int insertProjectRelationship(DeviceProjectRelationshipDO relationship);

    int updateDeviceProjectIfMatch(@Param("update") DeviceProjectAssignmentUpdate update);

    DeviceCustomerRelationshipDO selectCurrentCustomer(@Param("query") DeviceAssignmentLockQuery query);

    int closeCurrentCustomer(@Param("tenantId") Long tenantId,
                             @Param("deviceId") Long deviceId,
                             @Param("effectiveTo") LocalDateTime effectiveTo,
                             @Param("assignmentVersion") Long assignmentVersion);

    int insertCustomerRelationship(DeviceCustomerRelationshipDO relationship);

    int updateDeviceCustomerIfMatch(@Param("update") DeviceCustomerAssignmentUpdate update);

    default PageResult<DeviceProjectRelationshipDO> selectProjectHistoryPage(DeviceProjectHistoryPageQuery query) {
        long total = selectProjectHistoryCount(query);
        return total == 0 ? PageResult.empty() : new PageResult<>(selectProjectHistoryList(query), total);
    }

    List<DeviceProjectRelationshipDO> selectProjectHistoryList(@Param("query") DeviceProjectHistoryPageQuery query);

    long selectProjectHistoryCount(@Param("query") DeviceProjectHistoryPageQuery query);

    default PageResult<DeviceCustomerRelationshipDO> selectCustomerRelationshipPage(
            DeviceCustomerRelationshipPageQuery query) {
        long total = selectCustomerRelationshipCount(query);
        return total == 0 ? PageResult.empty() : new PageResult<>(selectCustomerRelationshipList(query), total);
    }

    List<DeviceCustomerRelationshipDO> selectCustomerRelationshipList(
            @Param("query") DeviceCustomerRelationshipPageQuery query);

    long selectCustomerRelationshipCount(@Param("query") DeviceCustomerRelationshipPageQuery query);

    Long selectProjectionDeviceForUpdate(@Param("tenantId") Long tenantId,
                                         @Param("deviceSn") String deviceSn);

    boolean existsAncestorProjectionOperation(@Param("tenantId") Long tenantId,
                                              @Param("eventId") String eventId);

    DeviceAncestorProjectionOperationDO selectLatestAncestorProjectionOperation(
            @Param("query") DeviceAncestorProjectionWatermarkQuery query);

    int deleteDeviceAncestors(@Param("tenantId") Long tenantId,
                              @Param("deviceSn") String deviceSn);

    int insertProjectAncestor(DeviceProjectAncestorDO ancestor);

    int insertAncestorProjectionOperation(DeviceAncestorProjectionOperationDO operation);

    int insertReconciliation(DeviceAssignmentReconciliationDO reconciliation);
}
