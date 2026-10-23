package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverChecklistMatchCandidateDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverChecklistMatchCandidateListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 实际命中规则与选择：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverChecklistMatchCandidateMapper extends BaseMapperX<CutoverChecklistMatchCandidateDO> {

    default List<CutoverChecklistMatchCandidateDO> selectListByParents(CutoverChecklistMatchCandidateListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistIds() == null || query.checklistIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverChecklistMatchCandidateDO>()
                .eq(CutoverChecklistMatchCandidateDO::getTenantId, query.tenantId())
                .in(CutoverChecklistMatchCandidateDO::getChecklistId, query.checklistIds())
                .orderByAsc(CutoverChecklistMatchCandidateDO::getId));
    }
}
