package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.ProjectScopedCodeMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.archivedocument.ArchiveDocumentDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query.ArchiveDocumentPageQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Objects;

@Mapper
public interface ArchiveDocumentMapper extends ProjectScopedCodeMapper<ArchiveDocumentDO> {
    ArchiveDocumentDO selectDeliveryOwnerForUpdate(@org.apache.ibatis.annotations.Param("query")
            cn.iocoder.yudao.module.pms.acceptance.dal.mysql.archivedocument.query.ArchiveDocumentDeliveryLockQuery query);


    default PageResult<ArchiveDocumentDO> selectPage(ArchiveDocumentPageQuery query) {
        Objects.requireNonNull(query.getVisibleProjectIds());
        if (query.getVisibleProjectIds().isEmpty()) {
            return new PageResult<>(List.of(), 0L);
        }
        return selectPage(query, new LambdaQueryWrapperX<ArchiveDocumentDO>()
                .eq(ArchiveDocumentDO::getTenantId, query.getTenantId())
                .in(ArchiveDocumentDO::getProjectId, query.getVisibleProjectIds())
                .eqIfPresent(ArchiveDocumentDO::getProjectId, query.getProjectId())
                .likeIfPresent(ArchiveDocumentDO::getCode, query.getCode())
                .likeIfPresent(ArchiveDocumentDO::getName, query.getName())
                .eqIfPresent(ArchiveDocumentDO::getDocumentType, query.getDocumentType())
                .eqIfPresent(ArchiveDocumentDO::getStatus, query.getStatus())
                .orderByDesc(ArchiveDocumentDO::getId));
    }

}
