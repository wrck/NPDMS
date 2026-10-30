package cn.iocoder.yudao.module.pms.cutover.service.checklist.port;

import java.util.List;
import java.util.Map;

/** 正式集成目录的消费边界。实现负责端点、凭据与调用超时；CUT 不保存这些信息。 */
public interface CutoverExternalDataPort {
    Definition definition();

    Response query(Request request);

    record Field(String key, String label, String valueType, boolean required) { }
    record Definition(String code, String name, List<Field> parameters, List<Field> responseFields) { }
    record Request(Long tenantId, Long actorId, Long taskId, Long checklistItemId,
                   Long itemDefinitionRevisionId, Long deviceId, String requestId,
                   Map<String, Object> parameters) { }
    /** values 是瞬时传输响应，按受控映射转为业务列；不得整包持久化。记录身份须来自正式来源。 */
    record Row(String recordId, String recordVersion, Map<String, Object> values) { }
    record Response(List<Row> rows) { }

    /** 仅允许对用户可见且已脱敏的业务失败编码/说明；不传输 URL、响应头或凭据。 */
    class Failure extends RuntimeException {
        private final String code;
        public Failure(String code, String safeDetail) { super(safeDetail); this.code = code; }
        public String code() { return code; }
    }
}
