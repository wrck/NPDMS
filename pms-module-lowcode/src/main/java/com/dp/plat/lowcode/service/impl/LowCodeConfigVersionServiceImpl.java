package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.dto.ConfigPackageDTO;
import com.dp.plat.lowcode.dto.VersionDiffDTO;
import com.dp.plat.lowcode.dto.VersionTreeNode;
import com.dp.plat.lowcode.entity.LowCodeConfigVersion;
import com.dp.plat.lowcode.mapper.LowCodeConfigVersionMapper;
import com.dp.plat.lowcode.service.LowCodeConfigVersionService;
import com.dp.plat.lowcode.version.VersionDiffCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 低代码配置版本管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class LowCodeConfigVersionServiceImpl
        extends ServiceImpl<LowCodeConfigVersionMapper, LowCodeConfigVersion>
        implements LowCodeConfigVersionService {

    private final VersionDiffCalculator diffCalculator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeConfigVersion createSnapshot(SnapshotContext context) {
        // 查询当前 DEV 环境最大版本号
        List<LowCodeConfigVersion> existing = baseMapper.selectList(
                new LambdaQueryWrapper<LowCodeConfigVersion>()
                        .eq(LowCodeConfigVersion::getConfigType, context.configType())
                        .eq(LowCodeConfigVersion::getConfigId, context.configId())
                        .eq(LowCodeConfigVersion::getEnvironment, "DEV")
                        .orderByDesc(LowCodeConfigVersion::getVersion));
        int nextVersion = existing.isEmpty() ? 1 : existing.get(0).getVersion() + 1;

        // 批次5-T1：设置 parentVersionId 为同环境前一版本（git parent commit 模型）
        // existing 已按 version desc 排序，第一个即为最大版本；首个版本 parent 保持 null
        Long parentVersionId = existing.isEmpty() ? null : existing.get(0).getId();

        LowCodeConfigVersion snapshot = LowCodeConfigVersion.builder()
                .configType(context.configType())
                .configId(context.configId())
                .configCode(context.configCode())
                .version(nextVersion)
                .snapshot(context.snapshot())
                .changeLog(context.changeLog())
                .status("ACTIVE")
                .environment("DEV")
                .parentVersionId(parentVersionId)
                .build();
        baseMapper.insert(snapshot);
        return snapshot;
    }

    @Override
    public List<LowCodeConfigVersion> getVersionHistory(String configType, Long configId) {
        return baseMapper.selectList(new LambdaQueryWrapper<LowCodeConfigVersion>()
                .eq(LowCodeConfigVersion::getConfigType, configType)
                .eq(LowCodeConfigVersion::getConfigId, configId)
                .orderByDesc(LowCodeConfigVersion::getVersion));
    }

    @Override
    public List<VersionTreeNode> getVersionTree(String configType, Long configId) {
        // 批次5-T1：按 parentVersionId 构建真正的分支树（无需排序，构建时按 parentId 索引）
        List<LowCodeConfigVersion> versions = baseMapper.selectList(
                new LambdaQueryWrapper<LowCodeConfigVersion>()
                        .eq(LowCodeConfigVersion::getConfigType, configType)
                        .eq(LowCodeConfigVersion::getConfigId, configId));
        if (versions.isEmpty()) {
            return new ArrayList<>();
        }

        // 检测是否所有版本 parentVersionId 都为 null（旧数据未回填场景）
        boolean hasAnyParent = versions.stream()
                .anyMatch(v -> v.getParentVersionId() != null);

        // 降级：旧数据无 parent 关系，按版本号升序线性构建（兼容）
        if (!hasAnyParent) {
            versions.sort((a, b) -> Integer.compare(a.getVersion(), b.getVersion()));
            VersionTreeNode root = toTreeNode(versions.get(0));
            VersionTreeNode current = root;
            for (int i = 1; i < versions.size(); i++) {
                VersionTreeNode child = toTreeNode(versions.get(i));
                current.getChildren().add(child);
                current = child;
            }
            List<VersionTreeNode> tree = new ArrayList<>();
            tree.add(root);
            return tree;
        }

        // 真分支树构建：按 parentVersionId 分组（null key 为根节点集合）
        Map<Long, List<LowCodeConfigVersion>> byParent = new HashMap<>();
        for (LowCodeConfigVersion v : versions) {
            Long key = v.getParentVersionId();
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }
        // 同一父节点下的子版本按 version 升序（稳定展示）
        for (List<LowCodeConfigVersion> kids : byParent.values()) {
            kids.sort((a, b) -> Integer.compare(a.getVersion(), b.getVersion()));
        }

        // 从 parentVersionId == null 的根节点开始递归构建
        // 用 LinkedHashMap 记录已构建节点 id → node，防止成环或孤儿节点丢失
        Map<Long, VersionTreeNode> built = new LinkedHashMap<>();
        List<VersionTreeNode> roots = new ArrayList<>();
        for (LowCodeConfigVersion root : byParent.getOrDefault(null, new ArrayList<>())) {
            roots.add(buildSubTree(root, byParent, built));
        }

        // 容错：若有孤儿节点（parentVersionId 指向不存在的 id），追加到根列表避免丢失
        for (LowCodeConfigVersion v : versions) {
            if (!built.containsKey(v.getId()) && v.getParentVersionId() != null) {
                roots.add(buildSubTree(v, byParent, built));
            }
        }
        return roots;
    }

    /** 递归构建子树：以 parent 为根，挂载其所有子节点 */
    private VersionTreeNode buildSubTree(LowCodeConfigVersion parent,
                                          Map<Long, List<LowCodeConfigVersion>> byParent,
                                          Map<Long, VersionTreeNode> built) {
        VersionTreeNode node = toTreeNode(parent);
        built.put(parent.getId(), node);
        List<LowCodeConfigVersion> kids = byParent.getOrDefault(parent.getId(), new ArrayList<>());
        for (LowCodeConfigVersion kid : kids) {
            // 防环：跳过已构建节点
            if (!built.containsKey(kid.getId())) {
                node.getChildren().add(buildSubTree(kid, byParent, built));
            }
        }
        return node;
    }

    /** 实体 → 树节点（含 versionId/parentVersionId/branch/tags；createTime 转字符串便于前端展示） */
    private VersionTreeNode toTreeNode(LowCodeConfigVersion v) {
        return VersionTreeNode.builder()
                .versionId(v.getId())
                .parentVersionId(v.getParentVersionId())
                .branch(v.getBranch())
                .tags(v.getTags())
                .version(v.getVersion())
                .configCode(v.getConfigCode())
                .changeLog(v.getChangeLog())
                .status(v.getStatus())
                .environment(v.getEnvironment())
                .createBy(v.getCreateBy())
                .createTime(v.getCreateTime() == null ? null : v.getCreateTime().toString())
                .children(new ArrayList<>())
                .build();
    }

    @Override
    public VersionDiffDTO diff(String configType, Long configId,
                                Integer fromVersion, Integer toVersion) {
        LowCodeConfigVersion from = getVersion(configType, configId, fromVersion);
        LowCodeConfigVersion to = getVersion(configType, configId, toVersion);
        return diffCalculator.diff(from.getSnapshot(), to.getSnapshot(),
                fromVersion, toVersion);
    }

    @Override
    public VersionDiffDTO rollbackPreview(String configType, Long configId, Integer targetVersion) {
        // 当前版本 = DEV 环境最新 ACTIVE 版本
        LowCodeConfigVersion from = baseMapper.selectOne(new LambdaQueryWrapper<LowCodeConfigVersion>()
                .eq(LowCodeConfigVersion::getConfigType, configType)
                .eq(LowCodeConfigVersion::getConfigId, configId)
                .eq(LowCodeConfigVersion::getEnvironment, "DEV")
                .eq(LowCodeConfigVersion::getStatus, "ACTIVE")
                .orderByDesc(LowCodeConfigVersion::getVersion)
                .last("LIMIT 1"));
        if (from == null) {
            throw new IllegalStateException("当前 DEV 环境无 ACTIVE 版本，无法回滚预览");
        }
        LowCodeConfigVersion to = getVersion(configType, configId, targetVersion);
        return diffCalculator.diff(from.getSnapshot(), to.getSnapshot(),
                from.getVersion(), targetVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeConfigVersion rollback(String configType, Long configId,
                                          Integer targetVersion, String changeLog) {
        LowCodeConfigVersion target = getVersion(configType, configId, targetVersion);
        // 回滚 = 用历史快照生成新版本（不删除历史）
        SnapshotContext context = new SnapshotContext(
                configType, configId, target.getConfigCode(),
                target.getSnapshot(),
                StringUtils.hasText(changeLog) ? changeLog : "回滚到版本 " + targetVersion);
        return createSnapshot(context);
    }

    @Override
    public ConfigPackageDTO exportPackage(String sourceEnvironment, List<String> configCodes) {
        List<LowCodeConfigVersion> versions = baseMapper.selectList(
                new LambdaQueryWrapper<LowCodeConfigVersion>()
                        .eq(LowCodeConfigVersion::getEnvironment, sourceEnvironment)
                        .in(LowCodeConfigVersion::getConfigCode, configCodes)
                        .eq(LowCodeConfigVersion::getStatus, "ACTIVE"));

        List<ConfigPackageDTO.PackageItem> items = versions.stream()
                .map(v -> ConfigPackageDTO.PackageItem.builder()
                        .configType(v.getConfigType())
                        .configId(v.getConfigId())
                        .configCode(v.getConfigCode())
                        .version(v.getVersion())
                        .snapshot(v.getSnapshot())
                        .build())
                .collect(Collectors.toList());

        return ConfigPackageDTO.builder()
                .sourceEnvironment(sourceEnvironment)
                .items(items)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importPackage(ConfigPackageDTO pkg) {
        for (ConfigPackageDTO.PackageItem item : pkg.getItems()) {
            // 在目标环境创建新版本快照
            LowCodeConfigVersion snapshot = LowCodeConfigVersion.builder()
                    .configType(item.getConfigType())
                    .configId(item.getConfigId())
                    .configCode(item.getConfigCode())
                    .version(item.getVersion())
                    .snapshot(item.getSnapshot())
                    .changeLog("从 " + pkg.getSourceEnvironment() + " 环境晋升")
                    .status("ACTIVE")
                    .environment(pkg.getTargetEnvironment())
                    .build();
            baseMapper.insert(snapshot);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeConfigVersion createBranch(String configType, Long configId, Long baseVersionId,
                                              String branchName, String changeLog) {
        // 校验分支名非空且不为 main
        if (!StringUtils.hasText(branchName)) {
            throw new IllegalArgumentException("分支名不能为空");
        }
        if ("main".equals(branchName)) {
            throw new IllegalArgumentException("不能使用 main 作为新分支名");
        }
        // 校验同 configType+configId 下不已有同名 branch
        Long branchCount = baseMapper.selectCount(new LambdaQueryWrapper<LowCodeConfigVersion>()
                .eq(LowCodeConfigVersion::getConfigType, configType)
                .eq(LowCodeConfigVersion::getConfigId, configId)
                .eq(LowCodeConfigVersion::getBranch, branchName));
        if (branchCount != null && branchCount > 0) {
            throw new IllegalStateException("分支名已存在: " + branchName);
        }
        // 查询分支起点版本作为新版本的基础快照
        LowCodeConfigVersion baseVersion = baseMapper.selectById(baseVersionId);
        if (baseVersion == null
                || !configType.equals(baseVersion.getConfigType())
                || !configId.equals(baseVersion.getConfigId())) {
            throw new IllegalArgumentException("分支起点版本不存在: " + baseVersionId);
        }
        // 计算新版本号：该 configType+configId+branch 下的 max(version) + 1（新分支从 1 开始）
        List<LowCodeConfigVersion> branchVersions = baseMapper.selectList(
                new LambdaQueryWrapper<LowCodeConfigVersion>()
                        .eq(LowCodeConfigVersion::getConfigType, configType)
                        .eq(LowCodeConfigVersion::getConfigId, configId)
                        .eq(LowCodeConfigVersion::getBranch, branchName)
                        .orderByDesc(LowCodeConfigVersion::getVersion));
        int nextVersion = branchVersions.isEmpty() ? 1 : branchVersions.get(0).getVersion() + 1;

        LowCodeConfigVersion newVersion = LowCodeConfigVersion.builder()
                .configType(configType)
                .configId(configId)
                .configCode(baseVersion.getConfigCode())
                .version(nextVersion)
                .snapshot(baseVersion.getSnapshot())
                .changeLog(StringUtils.hasText(changeLog) ? changeLog : "创建分支 " + branchName)
                .status("ACTIVE")
                .environment("DEV")
                .parentVersionId(baseVersionId)
                .branch(branchName)
                .build();
        baseMapper.insert(newVersion);
        return newVersion;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LowCodeConfigVersion addTag(String configType, Long configId, Long versionId, String tag) {
        if (!StringUtils.hasText(tag)) {
            throw new IllegalArgumentException("标签不能为空");
        }
        LowCodeConfigVersion version = baseMapper.selectOne(new LambdaQueryWrapper<LowCodeConfigVersion>()
                .eq(LowCodeConfigVersion::getId, versionId)
                .eq(LowCodeConfigVersion::getConfigType, configType)
                .eq(LowCodeConfigVersion::getConfigId, configId));
        if (version == null) {
            throw new IllegalArgumentException("版本不存在: " + configType + "/" + configId + "/id=" + versionId);
        }
        // 解析现有 tags（逗号分隔），若 tag 已存在直接返回
        List<String> existing = parseTagList(version.getTags());
        String trimmed = tag.trim();
        if (existing.contains(trimmed)) {
            return version;
        }
        existing.add(trimmed);
        version.setTags(String.join(",", existing));
        baseMapper.updateById(version);
        return version;
    }

    /** 解析标签字符串为列表（去空、去重保持顺序） */
    private List<String> parseTagList(String tags) {
        if (!StringUtils.hasText(tags)) {
            return new ArrayList<>();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private LowCodeConfigVersion getVersion(String configType, Long configId, Integer version) {
        LowCodeConfigVersion v = baseMapper.selectOne(new LambdaQueryWrapper<LowCodeConfigVersion>()
                .eq(LowCodeConfigVersion::getConfigType, configType)
                .eq(LowCodeConfigVersion::getConfigId, configId)
                .eq(LowCodeConfigVersion::getVersion, version));
        if (v == null) {
            throw new IllegalArgumentException("版本不存在: " + configType + "/" + configId + "/v" + version);
        }
        return v;
    }
}
