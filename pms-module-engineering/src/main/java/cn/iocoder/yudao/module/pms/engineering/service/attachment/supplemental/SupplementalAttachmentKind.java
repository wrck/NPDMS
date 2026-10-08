package cn.iocoder.yudao.module.pms.engineering.service.attachment.supplemental;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
@Getter @RequiredArgsConstructor
public enum SupplementalAttachmentKind {
 BRIEFING("SOL","briefing","BRIEFING_ATTACHMENT","交底手工附件","pms:sol-briefing",":generate");
 private final String module,type,purpose,title,permission,freezePermission;
 public String source(){return module+"."+purpose;}
}
