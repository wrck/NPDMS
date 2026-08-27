package cn.iocoder.yudao.module.pms.asset.domain.version;

import java.util.Objects;
import java.util.regex.Pattern;

public final class TechnicalNoticeMatcher {

    public enum Result {
        MATCHED,
        NOT_MATCHED,
        UNDETERMINED
    }

    private TechnicalNoticeMatcher() {
    }

    public static Result match(SoftwareVersion device, SoftwareVersion notice) {
        if (device == null || notice == null) {
            return Result.UNDETERMINED;
        }
        if (different(device.conpVersion(), notice.conpVersion())
                || different(device.conpType(), notice.conpType())
                || different(device.conpSeries(), notice.conpSeries())) {
            return Result.NOT_MATCHED;
        }
        Result markResult = matchMark(device.conpMark(), notice.conpMark());
        if (markResult != Result.MATCHED) {
            return markResult;
        }
        Result optionalResult = matchOptional(device.bootVersion(), notice.bootVersion());
        if (optionalResult != Result.MATCHED) {
            return optionalResult;
        }
        optionalResult = matchOptional(device.cpldVersion(), notice.cpldVersion());
        if (optionalResult != Result.MATCHED) {
            return optionalResult;
        }
        optionalResult = matchOptional(device.pcbVersion(), notice.pcbVersion());
        if (optionalResult != Result.MATCHED) {
            return optionalResult;
        }
        if (missing(device.conpVersion()) || missing(notice.conpVersion())
                || missing(device.conpType()) || missing(notice.conpType())
                || missing(device.conpSeries()) || missing(notice.conpSeries())) {
            return Result.UNDETERMINED;
        }
        return Result.MATCHED;
    }

    private static Result matchMark(String deviceMark, String noticeMark) {
        if (missing(deviceMark) || missing(noticeMark)) {
            return Result.UNDETERMINED;
        }
        String regex = Pattern.quote(noticeMark).replace("*", "\\E.*\\Q");
        return deviceMark.matches(regex) ? Result.MATCHED : Result.NOT_MATCHED;
    }

    private static Result matchOptional(String deviceValue, String noticeValue) {
        if (missing(noticeValue)) {
            return Result.MATCHED;
        }
        if (missing(deviceValue)) {
            return Result.UNDETERMINED;
        }
        return Objects.equals(deviceValue, noticeValue) ? Result.MATCHED : Result.NOT_MATCHED;
    }

    private static boolean different(String deviceValue, String noticeValue) {
        return !missing(deviceValue) && !missing(noticeValue) && !Objects.equals(deviceValue, noticeValue);
    }

    private static boolean missing(String value) {
        return value == null || value.isBlank();
    }
}
