package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.dto.CommentTreeNode;
import com.dp.plat.lowcode.entity.LowCodeComment;

import java.util.List;

public interface LowCodeCommentService extends IService<LowCodeComment> {
    List<LowCodeComment> listByConfig(String configType, Long configId);

    LowCodeComment addComment(LowCodeComment comment);

    /**
     * 查询线程化评论（按 parent_id 构建树）。
     *
     * <p>树在内存中一次性构建：先查出指定配置的全部评论，再按 parent_id 分组递归组装，
     * 避免递归查询 DB（N+1 问题）。顶层评论的 parentId 视为 0（null 也归入 0）。</p>
     *
     * @param configType 配置类型
     * @param configId   配置 ID
     * @return 顶层评论树节点列表
     */
    List<CommentTreeNode> listThreaded(String configType, Long configId);
}
