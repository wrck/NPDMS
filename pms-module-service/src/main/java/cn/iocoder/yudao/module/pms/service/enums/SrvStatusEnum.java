package cn.iocoder.yudao.module.pms.service.enums;

/**
 * 维保域状态枚举常量，集中维护便于 Service/Controller/前端字典对齐。
 */
public interface SrvStatusEnum {

    // 维保状态：0未生效 1生效中 2即将过期 3已过期 4已续保
    Integer SRV_MAINT_NOT_EFFECTIVE = 0;
    Integer SRV_MAINT_EFFECTIVE = 1;
    Integer SRV_MAINT_EXPIRING = 2;
    Integer SRV_MAINT_EXPIRED = 3;
    Integer SRV_MAINT_RENEWED = 4;
}
