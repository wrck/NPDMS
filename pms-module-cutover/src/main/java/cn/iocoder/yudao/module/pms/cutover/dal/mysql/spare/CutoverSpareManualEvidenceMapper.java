package cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare.CutoverSpareManualEvidenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.spare.query.SpareApplicationQueries;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CutoverSpareManualEvidenceMapper extends BaseMapperX<CutoverSpareManualEvidenceDO> {
    List<CutoverSpareManualEvidenceDO> selectByTask(@Param("query") SpareApplicationQueries.EvidenceByTask query);
}
