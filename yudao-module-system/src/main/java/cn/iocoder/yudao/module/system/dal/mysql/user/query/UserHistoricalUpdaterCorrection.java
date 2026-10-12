package cn.iocoder.yudao.module.system.dal.mysql.user.query;

/** Update command, with an exact locked target and the generated value being corrected. */
public record UserHistoricalUpdaterCorrection(Long tenantId, Long userId, String username, String expectedUpdater,
        java.time.LocalDateTime expectedUpdateTime,java.time.LocalDateTime restoredUpdateTime) {}
