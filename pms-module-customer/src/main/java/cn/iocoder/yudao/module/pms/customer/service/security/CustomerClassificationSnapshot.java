package cn.iocoder.yudao.module.pms.customer.service.security;

public record CustomerClassificationSnapshot(
        String departmentCode,
        String departmentName,
        String marketCode,
        String marketName,
        String systemCode,
        String systemName,
        String expendCode,
        String expendName,
        String industryCode,
        String industryName) {
}
