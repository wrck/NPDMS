package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 配置校验问题明细。需求：CUT-07。从属原业务修订，不独立发布。
 * @see <a href="https://baomidou.com/en/reference/annotation/">MyBatis-Plus字段映射</a>
 */
@TableName("cut_configuration_validation_issue")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverConfigurationValidationIssueDO {
    /** 本表主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属租户；必须与父对象一致 */
    private Long tenantId;
    /** 所属配置修订 */
    private Long configurationRevisionId;
    /** 本次校验序号 */
    private Integer validationNo;
    /** 问题顺序 */
    private Integer issueNo;
    /** 配置位置 */
    private String location;
    /** 校验问题说明 */
    private String message;
    /** 校验时间 */
    private LocalDateTime validateTime;
    /** 校验人 */
    private Long validateUserId;
    /** 创建人；历史投影由迁移执行身份记录，不冒充原业务办理人 */
    private Long createUserId;
    /** 本行创建时间；原业务发生时间另按来源字段保留 */
    private LocalDateTime createTime;
}
