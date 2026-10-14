package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskStageHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CutoverTaskStageHistoryMapper extends BaseMapperX<CutoverTaskStageHistoryDO> {
}
