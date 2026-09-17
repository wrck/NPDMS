package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

import java.util.List;

/** Locks a bounded batch of Owner rows by stable external source identity. */
public record AuthoritySourceKeysQuery(Long tenantId, String sourceSystem, List<String> sourceKeys) {}
