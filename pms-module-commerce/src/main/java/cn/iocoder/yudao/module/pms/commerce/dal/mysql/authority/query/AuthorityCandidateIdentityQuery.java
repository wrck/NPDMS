package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

public record AuthorityCandidateIdentityQuery(Long tenantId, String objectType, String sourceSystem,
                                              String sourceKey, String candidateVersion) {
}
