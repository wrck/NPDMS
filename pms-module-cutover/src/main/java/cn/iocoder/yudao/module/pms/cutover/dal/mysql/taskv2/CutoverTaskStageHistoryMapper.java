package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskStageHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CutoverTaskStageHistoryMapper extends BaseMapperX<CutoverTaskStageHistoryDO> {
}
