package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.dto.VersionDiffDTO;
import com.dp.plat.lowcode.dto.ConfigPackageDTO;
import com.dp.plat.lowcode.dto.VersionTreeNode;
import com.dp.plat.lowcode.entity.LowCodeConfigVersion;

import java.util.List;

/**
 * 低代码配置版本管理服务。
 */
public interface LowCodeConfigVersionService extends IService<LowCodeConfigVersion> {

    /**
     * 创建版本快照（不可变）。
     */
    LowCodeConfigVersion createSnapshot(SnapshotContext context);

    /**
     * 查询配置的版本历史。
     */
    List<LowCodeConfigVersion> getVersionHistory(String configType, Long configId);

    /**
     * 查询配置的版本树（按 parentVersionId 构建真正的分支树，支持多分支，借鉴 git parent commit 模型）。
     *
     * <p>每个版本通过 parentVersionId 指向其父版本，从根版本（parentVersionId 为 null）开始递归构建多分支树。
     * 若数据中所有版本 parentVersionId 都为 null（旧数据未回填），降级为按版本号升序线性构建。</p>
     */
    List<VersionTreeNode> getVersionTree(String configType, Long configId);

    /**
     * 对比两个版本的差异。
     */
    VersionDiffDTO diff(String configType, Long configId, Integer fromVersion, Integer toVersion);

    /**
     * 回滚到指定版本（用历史快照覆盖当前配置，生成新版本）。
     */
    LowCodeConfigVersion rollback(String configType, Long configId, Integer targetVersion, String changeLog);

    /**
     * 回滚预览：对比"当前版本"与"目标版本"的差异，不实际执行回滚。
     *
     * <p>当前版本 = DEV 环境最新 ACTIVE 版本；目标版本 = targetVersion 指定的历史版本。
     * 借鉴 OutSystems LifeTime 的回滚预览功能。</p>
     *
     * @param configType    配置类型
     * @param configId      配置 ID
     * @param targetVersion 要回滚到的目标版本号
     * @return 版本差异 DTO
     */
    VersionDiffDTO rollbackPreview(String configType, Long configId, Integer targetVersion);

    /**
     * 导出配置包（用于环境晋升）。
     */
    ConfigPackageDTO exportPackage(String sourceEnvironment, List<String> configCodes);

    /**
     * 导入配置包（环境晋升）。
     */
    void importPackage(ConfigPackageDTO pkg);

    /**
     * 创建分支（批次5-T1）。
     *
     * <p>从指定版本创建新分支，新分支的首个版本基于该版本的快照。
     * 借鉴 git branch：不修改原版本，仅在新版本上设置 branch 名与 parentVersionId。
     *
     * @param configType      配置类型
     * @param configId        配置 ID
     * @param baseVersionId   分支起点版本记录 ID
     * @param branchName      新分支名（不能为 main，不能与已有分支重名）
     * @param changeLog       变更说明
     * @return 新创建的分支首版本
     */
    LowCodeConfigVersion createBranch(String configType, Long configId, Long baseVersionId,
                                       String branchName, String changeLog);

    /**
     * 为版本添加标签（批次5-T1）。
     *
     * <p>向指定版本追加标签（逗号分隔），已存在的标签不重复添加。
     * 借鉴 git tag，但简化为字符串标签（不支持注释）。
     *
     * @param configType 配置类型
     * @param configId   配置 ID
     * @param versionId  版本记录 ID
     * @param tag        要添加的标签
     * @return 更新后的版本
     */
    LowCodeConfigVersion addTag(String configType, Long configId, Long versionId, String tag);

    /**
     * 快照上下文。
     */
    record SnapshotContext(String configType, Long configId, String configCode,
                           String snapshot, String changeLog) {
    }
}
