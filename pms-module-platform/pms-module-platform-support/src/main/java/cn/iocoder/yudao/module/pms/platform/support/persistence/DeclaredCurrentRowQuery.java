package cn.iocoder.yudao.module.pms.platform.support.persistence;

/** Explicit tenant + stable primary-key query; values are always bound parameters. */
public record DeclaredCurrentRowQuery(Long tenantId, Long entityId) { }
