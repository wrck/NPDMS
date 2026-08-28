package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import org.springframework.stereotype.Service;

@Service
public class CustomerFieldMaskingService {

    public enum ContactAccess {
        RAW,
        MASKED,
        HIDDEN
    }

    public CustomerRespVO apply(CustomerRespVO response, ContactAccess access) {
        if (response == null || access == null) {
            throw new IllegalArgumentException("客户联系方式裁剪参数不完整");
        }
        if (access == ContactAccess.HIDDEN) {
            response.setContactPhone(null);
            response.setContactEmail(null);
            return response;
        }
        if (access == ContactAccess.MASKED) {
            response.setContactPhone(maskPhone(response.getContactPhone()));
            response.setContactEmail(maskEmail(response.getContactEmail()));
        }
        return response;
    }

    private String maskPhone(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 7) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private String maskEmail(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int separator = value.indexOf('@');
        if (separator <= 0) {
            return "****";
        }
        return value.substring(0, 1) + "****" + value.substring(separator);
    }
}
