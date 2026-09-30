package cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface StagePlanCompletionMapper {
    StagePlanBatchDO current(@Param("query") StagePlanCompletionQuery query);

    /** 按任务冻结链接的批次 id 解析（读路径）；current 仅用于锁路径的当前生效批次。 */
    StagePlanBatchDO byId(@Param("query") StagePlanCompletionQuery query);
}
