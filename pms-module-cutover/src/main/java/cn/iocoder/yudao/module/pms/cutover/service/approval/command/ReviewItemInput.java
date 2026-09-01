package cn.iocoder.yudao.module.pms.cutover.service.approval.command;

public record ReviewItemInput(String itemCode, String decision, String unreasonableReason) {
}
