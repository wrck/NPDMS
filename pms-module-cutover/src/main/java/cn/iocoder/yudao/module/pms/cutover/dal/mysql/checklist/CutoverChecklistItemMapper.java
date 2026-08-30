package cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistItemsQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverChecklistItemMapper extends BaseMapperX<CutoverChecklistItemDO> {

    List<CutoverChecklistItemDO> selectListForUpdate(@Param("query") CutoverChecklistItemsQuery query);
}
