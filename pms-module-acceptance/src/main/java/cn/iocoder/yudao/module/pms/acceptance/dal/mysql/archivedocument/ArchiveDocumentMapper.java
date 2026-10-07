package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.ProjectScopedCodeMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.archivedocument.vo.ArchiveDocumentPageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArchiveDocumentMapper extends ProjectScopedCodeMapper<ArchiveDocumentDO> {
    ArchiveDocumentDO selectDeliveryOwnerForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query.ArchiveDocumentDeliveryLockQuery query);


    default PageResult<ArchiveDocumentDO> selectPage(ArchiveDocumentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ArchiveDocumentDO>()
                .eqIfPresent(ArchiveDocumentDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ArchiveDocumentDO::getCode, reqVO.getCode())
                .likeIfPresent(ArchiveDocumentDO::getName, reqVO.getName())
                .eqIfPresent(ArchiveDocumentDO::getDocumentType, reqVO.getDocumentType())
                .eqIfPresent(ArchiveDocumentDO::getStatus, reqVO.getStatus())
                .orderByDesc(ArchiveDocumentDO::getId));
    }

}
