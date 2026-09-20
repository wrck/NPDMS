package cn.iocoder.yudao.module.pms.service.dal.dataobject.srvofflinefile;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 离线巡检文件 DO
  * @deprecated 已随 pms_* 旧域退役：对应表已更名 pms_srv_offline_file_retired 且数据库侧仅允许查询（INSERT/UPDATE/DELETE 被触发器拒绝）；仅保留存量只读兼容，禁止新代码引用。
*/
@TableName("pms_srv_offline_file_retired")
@Data
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class SrvOfflineFileRetiredDO extends TenantBaseDO {

    /**
     * 文件编号
     */
    @TableId
    private Long id;
    /**
     * 所属巡检任务编号
     */
    private Long taskId;
    /**
     * 文件编码，任务内唯一
     */
    private String code;
    /**
     * 文件存储地址
     */
    private String fileUrl;
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    /**
     * 文件校验值
     */
    private String fileChecksum;
    /**
     * 解析状态
     *
     * 枚举 0待解析 1解析中 2解析成功 3解析失败
     */
    private Integer parseStatus;
    /**
     * 解析结果
     */
    private String parseResult;
    /**
     * 错误明细
     */
    private String errorDetail;
    /**
     * 解析人
     */
    private Long parsedBy;
    /**
     * 解析时间
     */
    private LocalDateTime parsedTime;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    private Integer version;

}
