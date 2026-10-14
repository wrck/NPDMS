package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverPlanFileDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverPlanFileListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 方案固定槽位文件：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverPlanFileMapper extends BaseMapperX<CutoverPlanFileDO> {

    default List<CutoverPlanFileDO> selectListByParents(CutoverPlanFileListQuery query) {
        if (query == null || query.tenantId() == null || query.planRevisionIds() == null || query.planRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverPlanFileDO>()
                .eq(CutoverPlanFileDO::getTenantId, query.tenantId())
                .in(CutoverPlanFileDO::getPlanRevisionId, query.planRevisionIds())
                .orderByAsc(CutoverPlanFileDO::getId));
    }
}
