package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 根对象已加锁后替换草稿匹配明细；提交历史不允许删除。 */
@Mapper
public interface CutoverChecklistStorageMapper {
    int deleteDraftInputDimensions(@Param("query") CutoverChecklistRowQuery query);
    int deleteDraftMatchCandidates(@Param("query") CutoverChecklistRowQuery query);
}
