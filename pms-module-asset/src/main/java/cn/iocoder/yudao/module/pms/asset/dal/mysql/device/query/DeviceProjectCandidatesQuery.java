package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query;

/** AST已有的项目关联键；仅用于与PROJ授权求交，不授予项目或设备权限。 */
public record DeviceProjectCandidatesQuery(Long tenantId) {}
