package cn.iocoder.yudao.module.pms.platform.api.businessmodel.event;

import java.util.List;

/**
 * 统一业务事件受控读取：执行后端按事件类型消费待处理事件；消费进度与去重
 * 由后端自身的检查点管理，读取不改变事件状态。
 * afterSequence 是消费游标（Outbox 行号）：读取只返回该行号之后的待处理事件，
 * 已检查点的事件不回填读取窗口，避免事件积压造成队头停滞。
 */
public interface BusinessEventPollPort {

    List<BusinessEventRecord> pollPending(String eventType, int limit, long afterSequence);
}
