import { describe, expect, it } from 'vitest'
import { parsePlanningImport, planningTemplate } from './solutionPlanningImport'

describe('planning CSV template/import', () => {
  it('round trips interface fields including leading zeroes, quoted commas and multiline remarks', () => {
    const text = planningTemplate('interface') + '001,0/01,0/02,100G,"SFP,100G","第一行\n第二行"\r\n'
    expect(parsePlanningImport('interface', text)).toEqual([{
      localDevice: '001', localPort: '0/01', peerPort: '0/02', portType: '100G', cableModel: 'SFP,100G', remark: '第一行\n第二行'
    }])
  })
  it('uses the exact IP page columns and preserves optional blanks and literal text', () => {
    expect(parsePlanningImport('ip', planningTemplate('ip') + '设备,001,192.0.2.1,255.255.255.0,,,"说明""原文"')).toEqual([
      { deviceName: '设备', port: '001', ip: '192.0.2.1', mask: '255.255.255.0', vrrp: '', gateway: '', remark: '说明"原文' }
    ])
  })
  it('rejects the other template, broken quoting and uneven rows before returning any data', () => {
    expect(() => parsePlanningImport('ip', planningTemplate('interface'))).toThrow('表头')
    expect(() => parsePlanningImport('interface', planningTemplate('interface') + 'a,b,c,d,e,f\na,b')).toThrow('第3行')
    expect(() => parsePlanningImport('interface', planningTemplate('interface') + '"unclosed')).toThrow('未闭合')
    expect(() => parsePlanningImport('interface', planningTemplate('interface') + '"a"b,c,d,e,f,g')).toThrow('引号格式')
  })
  it('does not turn an empty template into a new blank row', () => {
    expect(() => parsePlanningImport('interface', planningTemplate('interface') + ',,,,,\r\n')).toThrow('没有可导入')
  })
})
