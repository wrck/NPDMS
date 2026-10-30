package cn.iocoder.yudao.module.pms.project.api.reference;

/**
 * 项目编码查询 API（跨模块只读）。
 * <p>
 * 供实施域各模块为操作记录生成 "{项目编码}-{类型}-序号" 内部关联编码使用。
 */
public interface ProjectCodeQueryApi {

    /**
     * 查询项目编码。
     *
     * @param projectId 项目编号
     * @return 项目编码
     * @throws IllegalArgumentException 项目不存在时抛出
     */
    String getProjectCode(Long projectId);
}
