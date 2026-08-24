package cn.iocoder.yudao.module.pms.platform.api.authorization.dto;

import java.util.List;

public record AuthorizationGrantPageResult(List<AuthorizationGrantDTO> list, long total) {
}
