package com.dp.plat.lowcode.controller;

import com.dp.plat.common.annotation.OperLog;
import com.dp.plat.common.result.Result;
import com.dp.plat.lowcode.dto.CommentTreeNode;
import com.dp.plat.lowcode.entity.LowCodeComment;
import com.dp.plat.lowcode.service.LowCodeCommentService;
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

@Tag(name = "低代码评论", description = "LowCode comments")
@RestController
@RequestMapping("/api/lowcode/comment")
@RequiredArgsConstructor
public class LowCodeCommentController {

    private final LowCodeCommentService commentService;

    @Operation(summary = "查询配置评论列表")
    @GetMapping
    @PreAuthorize("@ss.hasPermission('lowcode:comment:list')")
    public Result<List<LowCodeComment>> list(@RequestParam String configType,
                                             @RequestParam Long configId) {
        return Result.ok(commentService.listByConfig(configType, configId));
    }

    @Operation(summary = "查询线程化评论（按 parent_id 构建树）")
    @GetMapping("/threaded")
    @PreAuthorize("@ss.hasPermission('lowcode:comment:list')")
    public Result<List<CommentTreeNode>> listThreaded(@RequestParam String configType,
                                                      @RequestParam Long configId) {
        return Result.ok(commentService.listThreaded(configType, configId));
    }

    @Operation(summary = "添加评论")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:comment:add')")
    @OperLog(title = "低代码评论", businessType = 1)
    public Result<LowCodeComment> add(@RequestBody LowCodeComment comment) {
        return Result.ok(commentService.addComment(comment));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:comment:del')")
    @OperLog(title = "低代码评论", businessType = 3)
    public Result<Void> delete(@PathVariable Long id) {
        commentService.removeById(id);
        return Result.ok();
    }
}
