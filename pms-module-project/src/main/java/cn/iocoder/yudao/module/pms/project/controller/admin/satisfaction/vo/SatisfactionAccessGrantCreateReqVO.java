package cn.iocoder.yudao.module.pms.project.controller.admin.satisfaction.vo;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SatisfactionAccessGrantCreateReqVO {
    @NotNull
    @Future
    private LocalDateTime expiresAt;
}
