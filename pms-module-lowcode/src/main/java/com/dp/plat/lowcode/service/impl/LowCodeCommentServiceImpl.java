package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.lowcode.dto.CommentTreeNode;
import com.dp.plat.lowcode.entity.LowCodeComment;
import com.dp.plat.lowcode.mapper.LowCodeCommentMapper;
import com.dp.plat.lowcode.service.LowCodeCommentService;
import com.dp.plat.notification.entity.Notification;
import com.dp.plat.notification.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeCommentServiceImpl extends ServiceImpl<LowCodeCommentMapper, LowCodeComment>
        implements LowCodeCommentService {

    private final INotificationService notificationService;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^\\]]+)\\]\\((\\d+)\\)");

    @Override
    public List<LowCodeComment> listByConfig(String configType, Long configId) {
        return list(new LambdaQueryWrapper<LowCodeComment>()
                .eq(LowCodeComment::getConfigType, configType)
                .eq(LowCodeComment::getConfigId, configId)
                .orderByAsc(LowCodeComment::getCreateTime));
    }

    @Override
    public List<CommentTreeNode> listThreaded(String configType, Long configId) {
        List<LowCodeComment> all = listByConfig(configType, configId);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        // 按 parent_id 分组，null parent_id 归入 0L 作为顶层
        Map<Long, List<LowCodeComment>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : 0L));
        return buildTree(byParent, 0L);
    }

    /**
     * 递归构建评论树（在内存中完成，不递归查询 DB）。
     *
     * @param byParent 按 parent_id 分组的评论映射
     * @param parentId 当前父节点 ID（0L 表示顶层）
     * @return 当前层级的树节点列表
     */
    private List<CommentTreeNode> buildTree(Map<Long, List<LowCodeComment>> byParent, Long parentId) {
        List<LowCodeComment> children = byParent.getOrDefault(parentId, Collections.emptyList());
        return children.stream().map(c -> {
            CommentTreeNode node = new CommentTreeNode(c);
            node.setReplies(buildTree(byParent, c.getId()));
            return node;
        }).collect(Collectors.toList());
    }

    @Override
    public LowCodeComment addComment(LowCodeComment comment) {
        // 解析 @mention 格式: @[用户名](用户ID)
        Set<Long> mentionedUserIds = parseMentions(comment.getContent());
        if (!mentionedUserIds.isEmpty()) {
            comment.setMentions(mentionedUserIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        save(comment);
        // 发送通知
        if (!mentionedUserIds.isEmpty()) {
            sendMentionNotifications(comment, mentionedUserIds);
        }
        return comment;
    }

    private Set<Long> parseMentions(String content) {
        if (content == null) return new HashSet<>();
        Set<Long> ids = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            ids.add(Long.valueOf(matcher.group(2)));
        }
        return ids;
    }

    private void sendMentionNotifications(LowCodeComment comment, Set<Long> userIds) {
        String title = "您在配置 " + comment.getConfigType() + "#" + comment.getConfigId() + " 中被 @提及";
        String content = comment.getUserName() + " 评论: " + comment.getContent();
        for (Long userId : userIds) {
            try {
                Notification notification = new Notification();
                notification.setUserId(userId);
                notification.setTitle(title);
                notification.setContent(content);
                notification.setCategory("LOWCODE_MENTION");
                notification.setBizType(comment.getConfigType());
                notification.setBizId(comment.getConfigId());
                Set<String> channels = new HashSet<>(Arrays.asList("IN_APP", "WS"));
                notificationService.multiChannelSend(notification, channels);
            } catch (Exception e) {
                log.warn("发送 @提及通知失败: userId={}", userId, e);
            }
        }
    }
}
