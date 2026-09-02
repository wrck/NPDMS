package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResponseFileDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResponseFilesQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SatisfactionResponseFileMapper extends BaseMapperX<SatisfactionResponseFileDO> {

    default List<SatisfactionResponseFileDO> selectListByResponse(SatisfactionResponseFilesQuery query) {
        return selectList(new LambdaQueryWrapperX<SatisfactionResponseFileDO>()
                .eq(SatisfactionResponseFileDO::getTenantId, query.tenantId())
                .eq(SatisfactionResponseFileDO::getResponseId, query.responseId())
                .orderByAsc(SatisfactionResponseFileDO::getFileRole)
                .orderByAsc(SatisfactionResponseFileDO::getFileSequence)
                .orderByAsc(SatisfactionResponseFileDO::getId));
    }
}
