package cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.ProjectScopedCodeMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 硬件安装 Mapper（FR-ENG-022）。
 */
@Mapper
public interface InstallationMapper extends ProjectScopedCodeMapper<InstallationDO> {

    java.util.List<InstallationDO> selectProjectAddressRecordsForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.ProjectInstallationAddressQuery query);

    java.util.List<cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.projection.InstallationCurrentAddressProjection> selectCurrentAddresses(
            @org.apache.ibatis.annotations.Param("query") cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationCurrentAddressesQuery query);

    default PageResult<InstallationDO> selectPage(InstallationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<InstallationDO>()
                .eqIfPresent(InstallationDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(InstallationDO::getCode, reqVO.getCode())
                .eqIfPresent(InstallationDO::getStatus, reqVO.getStatus())
                .eqIfPresent(InstallationDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(InstallationDO::getInstallerUserId, reqVO.getInstallerUserId())
                .orderByDesc(InstallationDO::getId));
    }

    default InstallationDO selectCurrentByEquipmentId(Long equipmentId) {
        return selectOne(new LambdaQueryWrapperX<InstallationDO>()
                .eq(InstallationDO::getEquipmentId, equipmentId)
                .eq(InstallationDO::getStatus, 2)
                .isNull(InstallationDO::getEffectiveTo)
                .orderByDesc(InstallationDO::getEffectiveFrom)
                .last("LIMIT 1"));
    }
}
