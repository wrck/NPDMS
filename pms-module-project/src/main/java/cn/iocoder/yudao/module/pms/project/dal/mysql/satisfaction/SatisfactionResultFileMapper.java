package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction.SatisfactionResultFileDO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query.SatisfactionResultFilesQuery;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SatisfactionResultFileMapper extends BaseMapperX<SatisfactionResultFileDO> {
    default List<SatisfactionResultFileDO> selectListByResult(SatisfactionResultFilesQuery query) {
        return selectList(new LambdaQueryWrapperX<SatisfactionResultFileDO>()
                .eq(SatisfactionResultFileDO::getTenantId, query.tenantId())
                .eq(SatisfactionResultFileDO::getResultId, query.resultId())
                .orderByAsc(SatisfactionResultFileDO::getFileRole)
                .orderByAsc(SatisfactionResultFileDO::getFileSequence)
                .orderByAsc(SatisfactionResultFileDO::getId));
    }
}
