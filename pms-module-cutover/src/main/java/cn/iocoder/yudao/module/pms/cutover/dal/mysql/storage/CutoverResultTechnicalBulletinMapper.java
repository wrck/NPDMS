package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultTechnicalBulletinDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultTechnicalBulletinListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 技术公告及逐条反馈结果行：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultTechnicalBulletinMapper extends BaseMapperX<CutoverResultTechnicalBulletinDO> {

    default List<CutoverResultTechnicalBulletinDO> selectListByParents(CutoverResultTechnicalBulletinListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultTechnicalBulletinDO>()
                .eq(CutoverResultTechnicalBulletinDO::getTenantId, query.tenantId())
                .in(CutoverResultTechnicalBulletinDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultTechnicalBulletinDO::getRowNo)
                .orderByAsc(CutoverResultTechnicalBulletinDO::getId));
    }
}
