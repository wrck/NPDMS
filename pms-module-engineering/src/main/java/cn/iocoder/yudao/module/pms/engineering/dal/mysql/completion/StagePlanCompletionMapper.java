package cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface StagePlanCompletionMapper {
    StagePlanBatchDO current(@Param("query") StagePlanCompletionQuery query);
}
