package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistCurrentResultQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistResultCloseUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistResultsQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverChecklistItemResultMapper extends BaseMapperX<CutoverChecklistItemResultDO> {

    CutoverChecklistItemResultDO selectCurrentForUpdate(@Param("query") CutoverChecklistCurrentResultQuery query);

    Integer selectMaxVersion(@Param("query") CutoverChecklistCurrentResultQuery query);

    java.util.List<CutoverChecklistItemResultDO> selectCurrentByChecklistForUpdate(
            @Param("query") CutoverChecklistResultsQuery query);

    java.util.List<CutoverChecklistItemResultDO> selectCurrentByChecklist(
            @Param("query") CutoverChecklistResultsQuery query);

    int closeCurrentIfMatch(@Param("query") CutoverChecklistResultCloseUpdate query);
}
