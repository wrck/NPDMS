package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query;
public record CompletionPdfBindingQuery(Long tenantId,Long certificateId,Long certificateVersion,String documentKind,String layoutKey) {}
