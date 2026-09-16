package com.dp.plat.lowcode.version;

import com.dp.plat.lowcode.dto.VersionDiffDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 版本 Diff 计算器。
 *
 * <p>对比两个 JSON 快照，返回结构化差异（新增/删除/修改 + 字段路径）。</p>
 */
@Component
@RequiredArgsConstructor
public class VersionDiffCalculator {

    private final ObjectMapper objectMapper;

    public VersionDiffDTO diff(String oldJson, String newJson, int fromVersion, int toVersion) {
        List<VersionDiffDTO.DiffEntry> entries = new ArrayList<>();
        try {
            JsonNode oldNode = objectMapper.readTree(oldJson);
            JsonNode newNode = objectMapper.readTree(newJson);
            compareNodes(oldNode, newNode, "", entries);
        } catch (Exception e) {
            throw new RuntimeException("Diff 计算失败", e);
        }
        return VersionDiffDTO.builder()
                .fromVersion(fromVersion)
                .toVersion(toVersion)
                .entries(entries)
                .build();
    }

    private void compareNodes(JsonNode oldNode, JsonNode newNode, String path,
                               List<VersionDiffDTO.DiffEntry> entries) {
        if (oldNode.isObject() && newNode.isObject()) {
            // 合并所有 key
            Iterator<String> oldFields = oldNode.fieldNames();
            Iterator<String> newFields = newNode.fieldNames();
            Set<String> allKeys = new TreeSet<>();
            oldFields.forEachRemaining(allKeys::add);
            newFields.forEachRemaining(allKeys::add);

            for (String key : allKeys) {
                String childPath = path.isEmpty() ? key : path + "." + key;
                if (!oldNode.has(key)) {
                    entries.add(VersionDiffDTO.DiffEntry.builder()
                            .changeType("ADDED")
                            .fieldPath(childPath)
                            .newValue(newNode.get(key).asText())
                            .build());
                } else if (!newNode.has(key)) {
                    entries.add(VersionDiffDTO.DiffEntry.builder()
                            .changeType("REMOVED")
                            .fieldPath(childPath)
                            .oldValue(oldNode.get(key).asText())
                            .build());
                } else {
                    compareNodes(oldNode.get(key), newNode.get(key), childPath, entries);
                }
            }
        } else if (oldNode.isArray() && newNode.isArray()) {
            compareArrays(oldNode, newNode, path, entries);
        } else {
            // 叶子节点：比较值
            if (!oldNode.equals(newNode)) {
                entries.add(VersionDiffDTO.DiffEntry.builder()
                        .changeType("MODIFIED")
                        .fieldPath(path)
                        .oldValue(oldNode.asText())
                        .newValue(newNode.asText())
                        .build());
            }
        }
    }

    /**
     * 数组对比：若元素为对象且含 {@code id} 字段，则按 id 对齐（避免重排产生伪 Diff）；
     * 否则回退到逐索引对比。
     */
    private void compareArrays(JsonNode oldNode, JsonNode newNode, String path,
                                List<VersionDiffDTO.DiffEntry> entries) {
        if (hasIdKey(oldNode) && hasIdKey(newNode)) {
            compareArraysById(oldNode, newNode, path, entries);
        } else {
            compareArraysByIndex(oldNode, newNode, path, entries);
        }
    }

    /** 判断数组元素是否均为对象且含 id 字段（仅检查首元素，约定数组元素同构）。 */
    private boolean hasIdKey(JsonNode arrayNode) {
        if (arrayNode == null || arrayNode.size() == 0) {
            return false;
        }
        JsonNode first = arrayNode.get(0);
        return first != null && first.isObject() && first.has("id");
    }

    /**
     * 按 id 对齐数组元素：
     * - 新数组中 id 不在旧数组 → ADDED
     * - 旧数组中 id 不在新数组 → REMOVED
     * - id 匹配的元素递归比较（path 用 [id=xxx] 标识，便于定位）
     */
    private void compareArraysById(JsonNode oldNode, JsonNode newNode, String path,
                                    List<VersionDiffDTO.DiffEntry> entries) {
        // 用 LinkedHashMap 保留旧数组顺序，便于稳定输出
        Map<String, JsonNode> oldById = new LinkedHashMap<>();
        for (JsonNode el : oldNode) {
            String id = el.get("id").asText();
            oldById.putIfAbsent(id, el);
        }
        Map<String, JsonNode> newById = new LinkedHashMap<>();
        for (JsonNode el : newNode) {
            String id = el.get("id").asText();
            newById.putIfAbsent(id, el);
        }

        // 先按新数组顺序处理（ADDED + 匹配递归）
        for (Map.Entry<String, JsonNode> e : newById.entrySet()) {
            String id = e.getKey();
            String childPath = path + "[id=" + id + "]";
            JsonNode oldEl = oldById.get(id);
            if (oldEl == null) {
                entries.add(VersionDiffDTO.DiffEntry.builder()
                        .changeType("ADDED")
                        .fieldPath(childPath)
                        .newValue(e.getValue().toString())
                        .build());
            } else {
                compareNodes(oldEl, e.getValue(), childPath, entries);
            }
        }
        // 再处理旧数组中已删除的 id
        for (Map.Entry<String, JsonNode> e : oldById.entrySet()) {
            if (!newById.containsKey(e.getKey())) {
                String childPath = path + "[id=" + e.getKey() + "]";
                entries.add(VersionDiffDTO.DiffEntry.builder()
                        .changeType("REMOVED")
                        .fieldPath(childPath)
                        .oldValue(e.getValue().toString())
                        .build());
            }
        }
    }

    /** 回退方案：逐索引对比（数组无 id 字段时使用）。 */
    private void compareArraysByIndex(JsonNode oldNode, JsonNode newNode, String path,
                                       List<VersionDiffDTO.DiffEntry> entries) {
        int maxLen = Math.max(oldNode.size(), newNode.size());
        for (int i = 0; i < maxLen; i++) {
            String childPath = path + "[" + i + "]";
            if (i >= oldNode.size()) {
                entries.add(VersionDiffDTO.DiffEntry.builder()
                        .changeType("ADDED")
                        .fieldPath(childPath)
                        .newValue(newNode.get(i).asText())
                        .build());
            } else if (i >= newNode.size()) {
                entries.add(VersionDiffDTO.DiffEntry.builder()
                        .changeType("REMOVED")
                        .fieldPath(childPath)
                        .oldValue(oldNode.get(i).asText())
                        .build());
            } else {
                compareNodes(oldNode.get(i), newNode.get(i), childPath, entries);
            }
        }
    }
}
