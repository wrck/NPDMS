package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_parse_part")
public class InspectionParsePartDO extends BaseBusinessEntity {
    private Long taskId;
    private Long executionId;
    private Integer partNo;
    private Integer firstCommandIndex;
    private Integer lastCommandIndex;
    private String requestId;
    private String parserTaskId;
    private String parserRelease;
    private String statusCode;
    private String failureCode;
    private java.time.LocalDateTime completedAt;
}
