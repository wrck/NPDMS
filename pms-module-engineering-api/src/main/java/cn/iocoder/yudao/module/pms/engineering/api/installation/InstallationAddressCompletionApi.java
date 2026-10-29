package cn.iocoder.yudao.module.pms.engineering.api.installation;
/** 工程Owner的项目硬件安装地址完成事实；不执行任务生命周期写入。 */
public interface InstallationAddressCompletionApi {
    record Query(Long tenantId, Long projectId) { }
    record Fact(int totalDevices, int addressedDevices, java.util.Set<Long> exemptDeviceIds) {
        public Fact {
            exemptDeviceIds = java.util.Set.copyOf(exemptDeviceIds);
            if (totalDevices < 0 || addressedDevices < 0 || exemptDeviceIds.size() > totalDevices
                    || addressedDevices > totalDevices - exemptDeviceIds.size())
                throw new IllegalArgumentException("INVALID_INSTALLATION_ADDRESS_COUNTS");
        }
        public Fact(int totalDevices, int addressedDevices) { this(totalDevices, addressedDevices, java.util.Set.of()); }
        public int exemptedDevices() { return exemptDeviceIds.size(); }
        public int requiredDevices() { return totalDevices - exemptedDevices(); }
        public boolean completed() { return totalDevices > 0 && requiredDevices() == addressedDevices; }
    }
    /** 必须在调用方事务内锁定设备范围和安装记录后核对。 */
    Fact lockAndInspect(Query query);
}
