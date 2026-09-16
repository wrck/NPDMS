package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
@TableName("pms_lowcode_comment")
public class LowCodeComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configType;
    private Long configId;
    private Long userId;
    private String userName;
    private String content;
    private String mentions; // 逗号分隔的用户 ID
    private Long parentId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
