package cn.iocoder.yudao.module.pms.integration.basesync;
public record BaseSyncConfiguration(String taskCode, long connectionId, int batchSize, int maxRows) {
    public BaseSyncConfiguration {
        if(taskCode==null || !taskCode.matches("BASE_P[0-9]{2}") || connectionId<=0
                || batchSize<1 || batchSize>2000 || maxRows<1 || maxRows>5_000_000)
            throw new IllegalArgumentException("新的同步任务配置无效");
    }
}
