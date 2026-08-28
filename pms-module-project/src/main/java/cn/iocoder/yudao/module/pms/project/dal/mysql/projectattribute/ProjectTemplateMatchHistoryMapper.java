package cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectattribute.ProjectTemplateMatchHistoryDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.query.ProjectTemplateMatchHistoryPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 专用append-only Mapper：物理接口仅公开插入和只读查询，不继承通用更新/删除能力。 */
@Mapper
public interface ProjectTemplateMatchHistoryMapper {

    int insert(@Param("row") ProjectTemplateMatchHistoryDO row);

    long selectCountPage(@Param("query") ProjectTemplateMatchHistoryPageQuery query);

    List<ProjectTemplateMatchHistoryDO> selectListPage(
            @Param("query") ProjectTemplateMatchHistoryPageQuery query);

    default PageResult<ProjectTemplateMatchHistoryDO> selectPage(ProjectTemplateMatchHistoryPageQuery query) {
        long total = selectCountPage(query);
        return total == 0 ? PageResult.empty() : new PageResult<>(selectListPage(query), total);
    }
}
