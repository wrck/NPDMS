package cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.query.CutoverDashboardCandidateQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverDashboardCandidateMapper {

    default List<CutoverDashboardCandidateRow> selectBatch(CutoverDashboardCandidateQuery query) {
        if (query.visibleProjectIds().isEmpty()) {
            return List.of();
        }
        return selectBatchScoped(query);
    }

    List<CutoverDashboardCandidateRow> selectBatchScoped(@Param("query") CutoverDashboardCandidateQuery query);
}
