package cn.iocoder.yudao.module.pms.lowcode.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeComponentMeta;
import cn.iocoder.yudao.module.pms.lowcode.mapper.LowCodeComponentMetaMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 低代码组件元数据 Controller（批次4-T8 增强为组件市场）。
 *
 * <p>提供组件元数据 CRUD + 市场功能（搜索/发布/下架/下载）。
 * 内置组件（builtin=1）不可删除。</p>
 */
@Tag(name = "低代码组件元数据", description = "LowCode component metadata & marketplace")
@RestController
@RequestMapping("/api/lowcode/component-meta")
@RequiredArgsConstructor
public class LowCodeComponentMetaController {

    private final LowCodeComponentMetaMapper mapper;

    @Operation(summary = "查询所有组件元数据")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:list')")
    public CommonResult<List<LowCodeComponentMeta>> list() {
        return CommonResult.success(mapper.selectList(null));
    }

    @Operation(summary = "组件详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:list')")
    public CommonResult<LowCodeComponentMeta> get(@PathVariable Long id) {
        return CommonResult.success(mapper.selectById(id));
    }

    @Operation(summary = "保存组件（新增/更新）")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:edit')")
    public CommonResult<LowCodeComponentMeta> save(@RequestBody LowCodeComponentMeta meta) {
        if (meta.getId() != null) {
            mapper.updateById(meta);
        } else {
            if (meta.getStatus() == null) {
                meta.setStatus("DRAFT");
            }
            if (meta.getSourceType() == null) {
                meta.setSourceType("CUSTOM");
            }
            if (meta.getVersion() == null) {
                meta.setVersion("1.0.0");
            }
            if (meta.getDownloadCount() == null) {
                meta.setDownloadCount(0);
            }
            mapper.insert(meta);
        }
        return CommonResult.success(meta);
    }

    @Operation(summary = "删除组件（内置组件不可删）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:edit')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        LowCodeComponentMeta meta = mapper.selectById(id);
        if (meta != null && meta.getBuiltin() != null && meta.getBuiltin() == 1) {
            return CommonResult.error(500, "内置组件不可删除");
        }
        mapper.deleteById(id);
        return CommonResult.success(null);
    }

    // ===================== 组件市场（批次4-T8） =====================

    @Operation(summary = "市场列表（仅已发布组件）")
    @GetMapping("/marketplace")
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:list')")
    public CommonResult<List<LowCodeComponentMeta>> marketplace() {
        return CommonResult.success(mapper.selectList(
                new LambdaQueryWrapper<LowCodeComponentMeta>()
                        .eq(LowCodeComponentMeta::getStatus, "PUBLISHED")
                        .orderByDesc(LowCodeComponentMeta::getDownloadCount)));
    }

    @Operation(summary = "搜索组件（按关键词/分类/标签）")
    @GetMapping("/search")
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:list')")
    public CommonResult<List<LowCodeComponentMeta>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag) {
        LambdaQueryWrapper<LowCodeComponentMeta> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LowCodeComponentMeta::getStatus, "PUBLISHED");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(LowCodeComponentMeta::getName, keyword)
                    .or().like(LowCodeComponentMeta::getDisplayName, keyword)
                    .or().like(LowCodeComponentMeta::getDescription, keyword));
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(LowCodeComponentMeta::getCategory, category);
        }
        if (tag != null && !tag.isBlank()) {
            wrapper.like(LowCodeComponentMeta::getTags, tag);
        }
        wrapper.orderByDesc(LowCodeComponentMeta::getDownloadCount);
        return CommonResult.success(mapper.selectList(wrapper));
    }

    @Operation(summary = "发布组件到市场")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:publish')")
    public CommonResult<Void> publish(@PathVariable Long id) {
        LowCodeComponentMeta meta = mapper.selectById(id);
        if (meta == null) {
            return CommonResult.error(500, "组件不存在");
        }
        meta.setStatus("PUBLISHED");
        mapper.updateById(meta);
        return CommonResult.success(null);
    }

    @Operation(summary = "下架组件")
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:publish')")
    public CommonResult<Void> unpublish(@PathVariable Long id) {
        LowCodeComponentMeta meta = mapper.selectById(id);
        if (meta == null) {
            return CommonResult.error(500, "组件不存在");
        }
        meta.setStatus("ARCHIVED");
        mapper.updateById(meta);
        return CommonResult.success(null);
    }

    @Operation(summary = "下载组件（增加下载计数并返回组件信息）")
    @PostMapping("/{id}/download")
    @PreAuthorize("@ss.hasPermission('lowcode:component:market:list')")
    public CommonResult<LowCodeComponentMeta> download(@PathVariable Long id) {
        LowCodeComponentMeta meta = mapper.selectById(id);
        if (meta == null) {
            return CommonResult.error(500, "组件不存在");
        }
        // 增加下载计数
        meta.setDownloadCount((meta.getDownloadCount() == null ? 0 : meta.getDownloadCount()) + 1);
        mapper.updateById(meta);
        return CommonResult.success(meta);
    }
}
