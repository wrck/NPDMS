package cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental;

import org.springframework.context.annotation.Bean;import org.springframework.context.annotation.Configuration;
@Configuration
public class SupplementalAttachmentPolicies {
 @Bean public SupplementalAttachmentFilePolicy briefingAttachmentFilePolicy(SupplementalAttachmentAccess access){return new SupplementalAttachmentFilePolicy(SupplementalAttachmentKind.BRIEFING,access);}
}
