package cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureDraftUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureSubmitUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureVersionUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverClosureMapper extends BaseMapperX<CutoverClosureDO> {
    CutoverClosureDO selectByTask(@Param("query") CutoverClosureRowQuery query);
    CutoverClosureDO selectByTaskForUpdate(@Param("query") CutoverClosureRowQuery query);
    int updateDraftIfMatch(@Param("query") CutoverClosureDraftUpdate query);
    int advanceDraftVersionIfMatch(@Param("query") CutoverClosureVersionUpdate query);
    int submitIfMatch(@Param("query") CutoverClosureSubmitUpdate query);
}
