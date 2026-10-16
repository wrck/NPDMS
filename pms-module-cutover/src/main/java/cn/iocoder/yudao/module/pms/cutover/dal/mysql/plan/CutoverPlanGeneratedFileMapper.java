package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan.CutoverPlanGeneratedFileDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanGeneratedFileQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CutoverPlanGeneratedFileMapper {
    CutoverPlanGeneratedFileDO selectLatest(@Param("query") CutoverPlanGeneratedFileQuery query);
    int insertIfCurrentDraft(@Param("row") CutoverPlanGeneratedFileDO row);
}
