package cn.iocoder.yudao.module.pms.platform.api.collection;

/** Owner-provided purpose and frozen commands, executed with the shared whole-batch timeout. */
public record CollectionExecutionProfile(String purpose, String commandText) {
    public CollectionExecutionProfile {
        if (!"PRECHECK".equals(purpose) && !"INSPECTION".equals(purpose)) throw new IllegalArgumentException("采集用途无效");
        if (commandText == null || ("PRECHECK".equals(purpose) ? !commandText.isEmpty() : commandText.isBlank())
                || commandText.indexOf('\0') >= 0)
            throw new IllegalArgumentException("采集用途与命令清单不一致");
    }
}
