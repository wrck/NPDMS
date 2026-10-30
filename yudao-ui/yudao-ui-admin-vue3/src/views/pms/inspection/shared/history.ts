// 对应 InspectionTaskStateMachine.Action 及巡检服务写入的办理事件；仅用于展示。
const actionNames: Readonly<Record<string, string>> = {
  CREATE: '创建巡检任务',
  SAVE_PREPARATION: '保存巡检准备资料',
  ASSIGN: '分配负责人',
  START_OFFLINE: '开始离线巡检',
  START_ONLINE: '开始在线巡检',
  PRECHECK_PASSED: '完成连接预检',
  EXECUTION_COMPLETE: '完成巡检执行',
  REPORT_READY: '确认巡检报告',
  ANNOTATE_RESULT: '保存问题标注',
  ANNOTATIONS_COMPLETE: '确认完成问题标注',
  UPDATE_ANNOTATIONS: '修订问题标注',
  ISSUES_CLOSED: '完成问题闭环',
  REQUEST_ARCHIVE: '申请归档',
  ARCHIVE: '确认归档',
  CANCEL: '取消巡检任务',
  OFFLINE_LOG_UPLOAD: '上传离线日志',
  COLLECTION_LOG_IMPORTED: '登记在线采集日志',
  REFRESH_LOG_PARSE: '刷新日志解析结果',
  ABANDON_LOG_PARSE: '停止采用日志解析结果',
  RESERVE_PRECHECK: '登记连接预检请求',
  RESERVE_COLLECT: '登记正式采集请求',
  REQUEST_ONLINE_CANCEL: '申请取消在线执行',
  CANCEL_ONLINE: '取消在线执行',
  CONFIRM_ONLINE_CANCEL: '确认在线执行取消结果',
  REFRESH_ONLINE: '刷新在线采集状态',
  REFRESH_PRECHECK: '刷新连接预检结果',
  UMC_RESULT_CAPTURED: '保存UMC正式判定结果',
  REPORT_GENERATION_FAILED: '巡检报告生成失败',
  RULE_MISSING_FEEDBACK: '提交规则缺失反馈',
  ISSUE_PROCESS: '追加问题处理记录',
  ISSUE_REQUEST_CLOSURE: '申请问题闭环',
  ISSUE_CONFIRM_CLOSURE: '确认问题闭环',
  ISSUE_RETURN_CLOSURE: '退回问题闭环申请'
}

export const inspectionEventActionName = (code?: string) =>
  (code && Object.prototype.hasOwnProperty.call(actionNames, code) && actionNames[code]) ||
  '历史办理记录'
