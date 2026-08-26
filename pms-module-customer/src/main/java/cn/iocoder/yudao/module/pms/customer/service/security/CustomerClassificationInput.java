package cn.iocoder.yudao.module.pms.customer.service.security;

public record CustomerClassificationInput(
        String departmentCode,
        String marketCode,
        String systemCode,
        String expendCode,
        String industryCode) {
}
