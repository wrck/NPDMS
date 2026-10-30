package cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import java.time.LocalDate;

/** 仅包含项目序列号表可读列；内部游标和原始地址不进入工作簿。 */
@Data
@ExcelIgnoreUnannotated
public class ProjectSerialExportRow {
    private Long deviceId;
    private String locationSnapshot;
    @ExcelProperty("序列号") private String sn;
    @ExcelProperty("产品编码") private String productCode;
    @ExcelProperty("产品名称") private String productName;
    @ExcelProperty("安装地址") private String location;
    @ExcelProperty("出厂软件版本") private String factorySoftwareVersion;
    @ExcelProperty("出厂conboot版本") private String factoryBootVersion;
    @ExcelProperty("出厂cpld版本") private String factoryCpldVersion;
    @ExcelProperty("官网版本") private String officialVersion;
    @ExcelProperty("在网软件版本分支") private String conpType;
    @ExcelProperty("在网软件版本") private String conpVersion;
    @ExcelProperty("在网conboot版本") private String bootVersion;
    @ExcelProperty("在网cpld版本") private String cpldVersion;
    @ExcelProperty("受影响技术公告") private String bulletin;
    @ExcelProperty("维保时长") private String warrantyDuration;
    @ExcelProperty("维保开始时间") private LocalDate warrantyStartDate;
    @ExcelProperty("维保结束时间") private LocalDate warrantyEndDate;
    @ExcelProperty("续保次数") private Long extendedCount;
}
