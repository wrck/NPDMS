package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

public record BusinessGrantUploadSessionQuery(
        Long tenantId, String ownerContext, String objectType, String objectId) {
}
