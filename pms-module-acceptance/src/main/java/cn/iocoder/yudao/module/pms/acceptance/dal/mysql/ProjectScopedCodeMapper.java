package cn.iocoder.yudao.module.pms.acceptance.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目内操作记录编码来源契约，供 {@link cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator} 推号。
 * <p>
 * 记录表唯一键 (project_id, code) 不含 deleted 列，软删除行仍占用编码；
 * 推号必须统计含软删除在内的全部既有编码，否则会反复撞键。
 */
public interface ProjectScopedCodeMapper<T> extends BaseMapperX<T> {

    /**
     * 查询项目内全部记录编码（含软删除行）。
     *
     * @param projectId 项目编号
     * @return 编码列表
     */
    List<String> selectRecordCodesIncludeDeleted(@Param("projectId") Long projectId);
}
