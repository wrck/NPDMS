package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import lombok.*;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;
import java.util.Set;

/** Fixed SQL rows for the separately authorized P04 direct task. */
public final class DirectAccountRows {
    private DirectAccountRows() {}
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
    public static class UserRow {
        private Long id;
        private String username;
        private String employeeNo;
        private String customInfo;
        @ToString.Exclude private String password;
        private String nickname;
        private String remark;
        private Long deptId;
        private Set<Long> postIds;
        private String email;
        private String mobile;
        private Integer sex;
        private String avatar;
        private Integer status;
        private Long tenantId;
        private String creator;
        private LocalDateTime createTime;
        private String updater;
        private LocalDateTime updateTime;
        private Boolean deleted;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
    public static class RoleRow {
        private Long id;
        private String name;
        private String code;
        private Integer sort;
        private Integer status;
        private Integer type;
        private String remark;
        private Integer dataScope;
        private Set<Long> dataScopeDeptIds;
        private Long tenantId;
        private String creator;
        private LocalDateTime createTime;
        private String updater;
        private LocalDateTime updateTime;
        private Boolean deleted;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
    public static class ScopeRow {
        private Long id;
        private Long userId;
        private Long companyId;
        private String companyCode;
        private String companyName;
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        private String scopeRole;
        private Boolean isPrimary;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveTo;
        private Integer status;
        private Integer version;
        private Long tenantId;
        private String creator;
        private LocalDateTime createTime;
        private String updater;
        private LocalDateTime updateTime;
        private Boolean deleted;
    }
}
