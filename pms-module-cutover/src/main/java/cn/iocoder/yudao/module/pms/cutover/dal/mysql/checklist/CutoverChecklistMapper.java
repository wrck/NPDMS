package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverChecklistMapper extends BaseMapperX<CutoverChecklistDO> {

    CutoverChecklistDO selectCurrentForUpdate(@Param("query") CutoverChecklistRowQuery query);
}
