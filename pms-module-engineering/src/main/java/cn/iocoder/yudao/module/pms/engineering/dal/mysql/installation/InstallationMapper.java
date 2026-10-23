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

    int carryHistoricalRecord(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationHistoricalCarryUpdate query);
    InstallationDO selectCurrentRecordForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationCurrentRecordQuery query);

    InstallationDO selectSourceCodeForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationSourceCodeQuery query);

    default PageResult<InstallationDO> selectFormalHistoryPage(
            cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.InstallationSourceHistoryPageQuery query) {
        if(query.visibleProjectIds()==null || query.visibleProjectIds().isEmpty())return new PageResult<>(java.util.List.of(),0L);
        var page=new cn.iocoder.yudao.framework.common.pojo.PageParam();page.setPageNo(query.pageNo());page.setPageSize(query.pageSize());
        return selectPage(page,new LambdaQueryWrapperX<InstallationDO>().eq(InstallationDO::getTenantId,query.tenantId())
                .in(InstallationDO::getProjectId,query.visibleProjectIds())
                .eqIfPresent(InstallationDO::getProjectId,query.projectId()).eqIfPresent(InstallationDO::getEquipmentId,query.equipmentId()).likeRight(InstallationDO::getCode,"DPPMS-")
                .orderByDesc(InstallationDO::getId));
    }
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
                .orderByDesc(InstallationDO::getEffectiveFrom));
    }
}
