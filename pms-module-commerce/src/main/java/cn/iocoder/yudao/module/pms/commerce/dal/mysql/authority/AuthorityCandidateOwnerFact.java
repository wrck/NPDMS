package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority;

public record AuthorityCandidateOwnerFact(Long ownerId, String ownerTable, String companyCode,
                                          String sourceVersion, String authorityStatus) {
}
