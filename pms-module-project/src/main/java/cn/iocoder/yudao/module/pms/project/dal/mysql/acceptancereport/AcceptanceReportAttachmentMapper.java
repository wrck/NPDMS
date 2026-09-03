package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportAttachmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AcceptanceReportAttachmentMapper extends BaseMapperX<AcceptanceReportAttachmentDO> {
    default List<AcceptanceReportAttachmentDO> selectByReportVersion(Long reportVersionId) {
        return selectList(new LambdaQueryWrapperX<AcceptanceReportAttachmentDO>()
                .eq(AcceptanceReportAttachmentDO::getReportVersionId, reportVersionId)
                .orderByAsc(AcceptanceReportAttachmentDO::getAttachmentSequence));
    }
}
