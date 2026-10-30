package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection;

import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionFeedbackQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InspectionFeedbackQueryMapper {
    List<InspectionFeedbackRow> selectPage(@Param("query") InspectionFeedbackQuery query);
    List<InspectionFeedbackRow> selectExportRows(@Param("query") InspectionFeedbackQuery query);
    long count(@Param("query") InspectionFeedbackQuery query);
    List<InspectionFeedbackStatistic> statistics(@Param("query") InspectionFeedbackQuery query);
}
