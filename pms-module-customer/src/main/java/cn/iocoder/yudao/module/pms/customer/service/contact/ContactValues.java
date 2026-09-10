package cn.iocoder.yudao.module.pms.customer.service.contact;

/** Contact information retained by the customer master or an independent project relation. */
public record ContactValues(String name, String department, String title, String mobile,
                            String phone, String email, String roleCode, String remark) {
}
