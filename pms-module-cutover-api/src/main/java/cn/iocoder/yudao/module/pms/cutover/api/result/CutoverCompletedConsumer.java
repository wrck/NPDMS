package cn.iocoder.yudao.module.pms.cutover.api.result;

/** 消费方 Owner 在自身事务内幂等接收结果，失败由平台 Outbox 重试。 */
public interface CutoverCompletedConsumer {
    String ownerContext();
    void accept(CutoverCompleted event);
}
