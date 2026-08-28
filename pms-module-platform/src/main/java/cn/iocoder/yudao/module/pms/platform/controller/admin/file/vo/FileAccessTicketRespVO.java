package cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileAccessTicketRespVO {
    private Long grantId;
    private String shortLivedUrl;
    private LocalDateTime expiresAt;
}
