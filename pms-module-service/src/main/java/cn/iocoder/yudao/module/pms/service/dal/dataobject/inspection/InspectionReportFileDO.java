package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_report_file")
public class InspectionReportFileDO extends BaseBusinessEntity {
    private Long taskId;
    private Long reportId;
    private String formatCode;
    private Long artifactId;
    private Integer fileVersionNo;
    private String referenceKey;
    private Long materialId;
    private String fileName;
    private String sha256;
}
