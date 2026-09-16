package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pms_lowcode_edit_lock")
public class LowCodeEditLock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configType;
    private Long configId;
    private Long userId;
    private String userName;
    private LocalDateTime acquiredAt;
    private LocalDateTime expireAt;
    private Integer renewCount;
    private LocalDateTime createTime;
}
