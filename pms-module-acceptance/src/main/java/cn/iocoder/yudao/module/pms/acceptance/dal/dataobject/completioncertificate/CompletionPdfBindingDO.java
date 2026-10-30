package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.completioncertificate;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("acc_completion_pdf_binding")
public class CompletionPdfBindingDO extends BaseBusinessEntity {
    private Long certificateId;
    private Long certificateVersion;
    private String documentKind;
    private String layoutKey;
    private Long materialId;
}
