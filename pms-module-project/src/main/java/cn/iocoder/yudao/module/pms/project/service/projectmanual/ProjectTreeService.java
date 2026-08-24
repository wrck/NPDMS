package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 项目树与进度汇总 Service（F-PM02 / PM-02）
 * <p>
 * V1.7进度与权重兼容入口。项目树查询和移动已收敛到版本化ProjectTree运行面。
 */
public interface ProjectTreeService {

    /**
     * 整组设置直接子项目人工权重；请求必须完整覆盖当前直接子项目且合计为 100%。
     */
    void updateChildWeights(Long projectId, Map<Long, BigDecimal> childWeights);

    /**
     * 进度汇总：直接子项目进度列表 + 归一化权重 + 汇总进度。
     */
    ProjectProgress getProgress(Long projectId);

    /**
     * 直接子项目进度项。
     */
    record ChildProgress(Long projectId, String projectCode, String projectName,
                         BigDecimal progress, BigDecimal normalizedWeight, String weightSource) {
    }

    /**
     * 项目进度汇总结果。
     */
    record ProjectProgress(BigDecimal aggregate, java.util.List<ChildProgress> children) {
    }
}
