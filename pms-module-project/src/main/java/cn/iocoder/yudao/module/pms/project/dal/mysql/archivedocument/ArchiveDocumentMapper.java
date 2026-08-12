package cn.iocoder.yudao.module.pms.project.dal.mysql.archivedocument;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.controller.admin.archivedocument.vo.ArchiveDocumentPageReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.archivedocument.ArchiveDocumentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArchiveDocumentMapper extends BaseMapperX<ArchiveDocumentDO> {

    default ArchiveDocumentDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<ArchiveDocumentDO>()
                .eq(ArchiveDocumentDO::getProjectId, projectId)
                .eq(ArchiveDocumentDO::getCode, code));
    }

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
