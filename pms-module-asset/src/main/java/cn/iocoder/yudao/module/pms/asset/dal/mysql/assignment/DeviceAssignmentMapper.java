package cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceAncestorProjectionOperationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceAssignmentReconciliationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectAncestorDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAncestorProjectionWatermarkQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceCustomerAssignmentUpdate;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceProjectAssignmentUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

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
