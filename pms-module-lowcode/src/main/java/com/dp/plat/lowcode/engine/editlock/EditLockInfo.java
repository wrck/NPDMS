package com.dp.plat.lowcode.engine.editlock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditLockInfo {
    private String configType;
    private Long configId;
    private Long userId;
    private String userName;
    private LocalDateTime acquiredAt;
    private LocalDateTime expireAt;
    private boolean acquired;
    private String message;
}
