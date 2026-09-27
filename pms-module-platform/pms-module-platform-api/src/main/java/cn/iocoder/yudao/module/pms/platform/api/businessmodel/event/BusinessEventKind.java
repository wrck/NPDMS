package cn.iocoder.yudao.module.pms.platform.api.businessmodel.event;

/** 业务事件类别：变更与结果形成/失效按真实业务动作输出；保存不默认形成完成结果。 */
public enum BusinessEventKind {
    CHANGED, FORMED, INVALIDATED
}
