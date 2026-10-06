package cn.iocoder.yudao.module.pms.engineering.service.attachment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NativeAttachmentPolicyConfiguration {
    @Bean NativeAttachmentFilePolicy configurationAttachments(NativeAttachmentAccess access){return new NativeAttachmentFilePolicy(NativeAttachmentKind.CONFIGURATION,access);}
    @Bean NativeAttachmentFilePolicy jointtestAttachments(NativeAttachmentAccess access){return new NativeAttachmentFilePolicy(NativeAttachmentKind.JOINT_TEST,access);}
    @Bean NativeAttachmentFilePolicy externalprocurementAttachments(NativeAttachmentAccess access){return new NativeAttachmentFilePolicy(NativeAttachmentKind.EXTERNAL_PROCUREMENT,access);}
    @Bean NativeAttachmentFilePolicy outsourceAttachments(NativeAttachmentAccess access){return new NativeAttachmentFilePolicy(NativeAttachmentKind.OUTSOURCE,access);}
    @Bean NativeAttachmentFilePolicy materialrequisitionAttachments(NativeAttachmentAccess access){return new NativeAttachmentFilePolicy(NativeAttachmentKind.MATERIAL_REQUISITION,access);}
    @Bean NativeAttachmentFilePolicy materialexchangeAttachments(NativeAttachmentAccess access){return new NativeAttachmentFilePolicy(NativeAttachmentKind.MATERIAL_EXCHANGE,access);}
}
