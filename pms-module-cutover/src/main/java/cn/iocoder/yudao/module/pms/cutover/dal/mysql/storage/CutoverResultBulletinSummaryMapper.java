package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultBulletinSummaryDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultBulletinSummaryListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 公告查询总体反馈：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultBulletinSummaryMapper extends BaseMapperX<CutoverResultBulletinSummaryDO> {

    default List<CutoverResultBulletinSummaryDO> selectListByParents(CutoverResultBulletinSummaryListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultBulletinSummaryDO>()
                .eq(CutoverResultBulletinSummaryDO::getTenantId, query.tenantId())
                .in(CutoverResultBulletinSummaryDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultBulletinSummaryDO::getId));
    }
}
