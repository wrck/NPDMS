package cn.iocoder.yudao.module.system.api.sms;

/** Availability of an optional SMS notification using the platform SMS configuration. */
public interface SmsNotificationAvailabilityApi {

    /** Returns true only when both the template and its channel exist and are enabled. */
    boolean isEnabled(String templateCode);
}
