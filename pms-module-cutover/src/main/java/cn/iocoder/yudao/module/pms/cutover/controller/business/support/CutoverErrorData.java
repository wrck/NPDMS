package cn.iocoder.yudao.module.pms.cutover.controller.business.support;

/** 不含内部异常、凭据或可变业务实体的命令恢复信息。 */
public record CutoverErrorData(String category, String reasonCode, String recoveryAction) { }
