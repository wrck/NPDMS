package cn.iocoder.yudao.module.pms.asset.api.device.dto;

import java.util.List;

public record SerialScopeValidationResult(boolean valid, List<String> missingSerialNumbers,
                                          List<String> unavailableSerialNumbers,
                                          List<String> duplicateSerialNumbers) {
}
