package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistDraftTouchUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRematchUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistSubmitUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverChecklistMapper extends BaseMapperX<CutoverChecklistDO> {

    CutoverChecklistDO selectCurrentForUpdate(@Param("query") CutoverChecklistRowQuery query);

    CutoverChecklistDO selectCurrent(@Param("query") CutoverChecklistRowQuery query);

    int touchDraftIfMatch(@Param("query") CutoverChecklistDraftTouchUpdate query);

    int submitIfMatch(@Param("query") CutoverChecklistSubmitUpdate query);

    int rematchIfMatch(@Param("query") CutoverChecklistRematchUpdate query);
}
