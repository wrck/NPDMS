package cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** Append-only history, not a mutable copy of the current record. */
@Data
@TableName("cus_contact_history")
public class ContactHistoryDO {
    @TableId private Long id;
    private Long tenantId;
    private Long customerContactId;
    private Long projectRelationId;
    private Long projectId;
    private String actionCode;
    private String beforeValues;
    private String afterValues;
    private Long actorUserId;
    private LocalDateTime occurredAt;
}
