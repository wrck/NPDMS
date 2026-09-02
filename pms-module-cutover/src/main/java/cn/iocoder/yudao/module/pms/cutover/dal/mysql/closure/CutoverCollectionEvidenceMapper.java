package cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverCollectionEvidenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureChildrenQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverCollectionEvidenceMapper extends BaseMapperX<CutoverCollectionEvidenceDO> {
    List<CutoverCollectionEvidenceDO> selectListByClosure(@Param("query") CutoverClosureChildrenQuery query);
    List<CutoverCollectionEvidenceDO> selectListByClosureForUpdate(@Param("query") CutoverClosureChildrenQuery query);
    long selectUnresolvedDispatchCount(@Param("query") CutoverClosureChildrenQuery query);
}
