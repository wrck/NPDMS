package cn.iocoder.yudao.module.pms.service.dal.dataobject.srvrule;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 巡检规则库 DO
  * @deprecated 已随 pms_* 旧域退役：对应表已更名 pms_srv_rule_retired 且数据库侧仅允许查询（INSERT/UPDATE/DELETE 被触发器拒绝）；仅保留存量只读兼容，禁止新代码引用。
*/
@TableName("pms_srv_rule_retired")
@Data
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class SrvRuleRetiredDO extends TenantBaseDO {

    /**
     * 规则编号
     */
    @TableId
    private Long id;
    /**
     * 规则编码，全局唯一
     */
    private String code;
    /**
     * 规则名称
     */
    private String name;
    /**
     * 规则类型 ONLINE 在线 / OFFLINE 离线
     */
    private String ruleType;
    /**
     * 规则版本号
     */
    private String ruleVersion;
    /**
     * 规则内容（CLI命令、解析表达式、阈值、严重级别等）
     */
    private String content;
    /**
     * 状态
     *
     * 枚举 0草稿 1已发布 2已停用
     */
    private Integer status;
    /**
     * 生效时间
     */
    private LocalDateTime effectiveTime;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    private Integer version;

}
