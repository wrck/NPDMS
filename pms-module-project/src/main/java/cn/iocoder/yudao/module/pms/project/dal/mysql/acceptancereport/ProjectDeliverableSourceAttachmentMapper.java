package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectDeliverableSourceAttachmentMapper extends BaseMapperX<ProjectDeliverableSourceAttachmentDO> {

    default List<ProjectDeliverableSourceAttachmentDO> selectBySourceVersion(Long sourceVersionId) {
        return selectList(new LambdaQueryWrapperX<ProjectDeliverableSourceAttachmentDO>()
                .eq(ProjectDeliverableSourceAttachmentDO::getDeliverableSourceVersionId, sourceVersionId)
                .orderByAsc(ProjectDeliverableSourceAttachmentDO::getAttachmentSequence));
    }
}
