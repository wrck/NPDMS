package com.dp.plat.lowcode.dto;

import com.dp.plat.lowcode.entity.LowCodeComment;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 评论线程树节点（按 parent_id 构建的多级回复树）。
 *
 * <p>树在内存中一次性构建：先查出指定配置的全部评论，再按 parent_id 分组递归组装。
 * 顶层评论的 parentId 视为 0（null 也归入 0），避免 N+1 查询。</p>
 */
@Data
@NoArgsConstructor
public class CommentTreeNode {

    /** 当前评论 */
    private LowCodeComment comment;

    /** 子回复列表 */
    private List<CommentTreeNode> replies = new ArrayList<>();

    public CommentTreeNode(LowCodeComment comment) {
        this.comment = comment;
    }
}
