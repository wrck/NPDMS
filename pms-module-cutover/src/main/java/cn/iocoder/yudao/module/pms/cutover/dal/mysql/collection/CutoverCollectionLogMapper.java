package cn.iocoder.yudao.module.pms.cutover.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.collection.CutoverCollectionLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CutoverCollectionLogMapper extends BaseMapperX<CutoverCollectionLogDO> {
    default CutoverCollectionLogDO findResult(cn.iocoder.yudao.module.pms.cutover.dal.mysql.collection.query.CutoverCollectionResultQuery query) {
        return selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<CutoverCollectionLogDO>()
                .eq(CutoverCollectionLogDO::getTenantId, query.tenantId())
                .eq(CutoverCollectionLogDO::getPlatformTaskId, query.platformTaskId())
                .eq(CutoverCollectionLogDO::getResultVersion, query.resultVersion()));
    }
}
