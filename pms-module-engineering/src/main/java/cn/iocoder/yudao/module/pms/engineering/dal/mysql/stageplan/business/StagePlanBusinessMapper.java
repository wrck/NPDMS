package cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.business;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.business.StagePlanBusinessDO;
import cn.iocoder.yudao.module.pms.platform.support.business.BusinessMapper;
import org.apache.ibatis.annotations.Mapper;
@Mapper public interface StagePlanBusinessMapper extends BusinessMapper<StagePlanBusinessDO> {
    record DetailUpdate(Long tenantId,Long batchId,Long itemId,java.time.LocalDate planStart,java.time.LocalDate planEnd,String remark,String updater) { }
    int updateDetail(@org.apache.ibatis.annotations.Param("query") DetailUpdate query);
}
