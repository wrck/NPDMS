import { expect, it } from 'vitest'
import { inspectionEventActionName } from './history'

it('区分在线请求取消与已确认取消，避免把申请显示为完成', () => {
  expect(inspectionEventActionName('REQUEST_ONLINE_CANCEL')).toBe('申请取消在线执行')
  expect(inspectionEventActionName('CANCEL_ONLINE')).toBe('取消在线执行')
  expect(inspectionEventActionName('CONFIRM_ONLINE_CANCEL')).toBe('确认在线执行取消结果')
})

it('区分判定引用、执行完成、报告确认和闭环申请各环节', () => {
  expect(inspectionEventActionName('UMC_RESULT_CAPTURED')).toBe('保存UMC正式判定结果')
  expect(inspectionEventActionName('EXECUTION_COMPLETE')).toBe('完成巡检执行')
  expect(inspectionEventActionName('REPORT_READY')).toBe('确认巡检报告')
  expect(inspectionEventActionName('ISSUE_REQUEST_CLOSURE')).toBe('申请问题闭环')
  expect(inspectionEventActionName('ISSUE_CONFIRM_CLOSURE')).toBe('确认问题闭环')
})

it('未知、空值及对象原型属性不显示编码或伪造动作', () => {
  for (const code of [undefined, '', 'LEGACY_UNKNOWN', '__proto__', 'toString']) {
    expect(inspectionEventActionName(code)).toBe('历史办理记录')
  }
})
