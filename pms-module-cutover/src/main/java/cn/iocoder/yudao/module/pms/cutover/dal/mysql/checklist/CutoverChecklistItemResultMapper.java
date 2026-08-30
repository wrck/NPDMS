package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemResultDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistCurrentResultQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverChecklistItemResultMapper extends BaseMapperX<CutoverChecklistItemResultDO> {

    CutoverChecklistItemResultDO selectCurrentForUpdate(@Param("query") CutoverChecklistCurrentResultQuery query);
}
