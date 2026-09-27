package cn.iocoder.yudao.module.pms.platform.api.businessmodel.event;

/**
 * 业务事件端口。实现必须在调用方业务事务内追加（Outbox 语义），
 * 不同步依赖外部消费者；缺依赖时抛出显式异常而非静默丢弃。
 */
public interface BusinessEventPort {

    void append(BusinessEventRecord event);
}
