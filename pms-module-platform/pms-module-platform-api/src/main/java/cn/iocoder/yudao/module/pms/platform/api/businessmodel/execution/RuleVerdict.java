package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import java.util.List;

/**
 * 规则判断：satisfied 为 null 表示未知（三值逻辑，未知取反不得通过）；
 * collectionComplete=false 时不得声称"全部满足"。
 */
public record RuleVerdict(Boolean satisfied, boolean collectionComplete, String basis, List<String> diagnostics) {
}
