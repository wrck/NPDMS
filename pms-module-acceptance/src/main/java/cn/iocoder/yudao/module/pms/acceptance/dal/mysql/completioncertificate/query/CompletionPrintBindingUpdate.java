package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.completioncertificate.query;
/** Replaces both optional draft print selections, including an explicit return to the built-in layout. */
public record CompletionPrintBindingUpdate(Long tenantId, Long certificateId, Long expectedVersion, String actorRef,
        Long completionTemplateId, Long completionRevisionId, String completionSnapshot,
        Long satisfactionTemplateId, Long satisfactionRevisionId, String satisfactionSnapshot) { }
