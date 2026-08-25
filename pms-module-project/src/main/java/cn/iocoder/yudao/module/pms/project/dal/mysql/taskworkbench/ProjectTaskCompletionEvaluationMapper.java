package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskCompletionEvaluationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 项目任务完成判定追加事实入口。 */
@Mapper
public interface ProjectTaskCompletionEvaluationMapper {

    int insertEvaluation(@Param("evaluation") ProjectTaskCompletionEvaluationDO evaluation);
}
