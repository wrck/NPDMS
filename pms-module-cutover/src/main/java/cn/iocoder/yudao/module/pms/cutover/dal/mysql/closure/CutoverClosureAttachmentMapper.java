package cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureAttachmentDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureChildrenQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverClosureAttachmentMapper extends BaseMapperX<CutoverClosureAttachmentDO> {
    List<CutoverClosureAttachmentDO> selectListByClosure(@Param("query") CutoverClosureChildrenQuery query);
    List<CutoverClosureAttachmentDO> selectListByClosureForUpdate(@Param("query") CutoverClosureChildrenQuery query);
}
