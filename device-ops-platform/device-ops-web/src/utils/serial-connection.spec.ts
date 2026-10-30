import { describe, expect, it } from 'vitest'

import {
  DEFAULT_SERIAL_PARAMS,
  DEFAULT_SERIAL_PROMPTS,
  SERIAL_BAUD_RATES,
  SERIAL_FLOW_CONTROL_OPTIONS,
  SERIAL_PARITY_OPTIONS,
  SERIAL_STOP_BIT_OPTIONS,
  serialParamFields,
  validateSerialConnection
} from '@/utils/serial-connection'

describe('serial connection options', () => {
  it('offers console-standard baud rates with 9600 default', () => {
    expect(SERIAL_BAUD_RATES).toContain(9600)
    expect(SERIAL_BAUD_RATES[0]).toBe(4800)
    expect(SERIAL_BAUD_RATES.at(-1)).toBe(115200)
    expect(DEFAULT_SERIAL_PARAMS.baudRate).toBe(9600)
    expect(DEFAULT_SERIAL_PARAMS.dataBits).toBe(8)
    expect(DEFAULT_SERIAL_PARAMS.parity).toBe('NONE')
    expect(DEFAULT_SERIAL_PARAMS.stopBits).toBe(1)
    expect(DEFAULT_SERIAL_PARAMS.flowControl).toBe('NONE')
  })

  it('provides chinese labels for parity and flow control', () => {
    expect(SERIAL_PARITY_OPTIONS.find((option) => option.value === 'NONE')?.label).toContain('无校验')
    expect(SERIAL_FLOW_CONTROL_OPTIONS.find((option) => option.value === 'NONE')?.label).toContain('无')
  })

  it('serializes param fields with stop-bit options', () => {
    expect(serialParamFields()).toEqual(
      expect.arrayContaining(['baudRate', 'dataBits', 'parity', 'stopBits', 'flowControl'])
    )
    expect(SERIAL_STOP_BIT_OPTIONS.map((option) => option.value)).toEqual([1, 2])
  })

  it('defaults prompts mirror telnet defaults', () => {
    expect(DEFAULT_SERIAL_PROMPTS.login).toContain('login')
    expect(DEFAULT_SERIAL_PROMPTS.lineEnding).toBe('AUTO')
  })
})

describe('validateSerialConnection', () => {
  it('accepts a complete serial payload with port 0', () => {
    expect(
      validateSerialConnection({
        host: 'COM3',
        port: 0,
        serialParams: { ...DEFAULT_SERIAL_PARAMS },
        serialPrompts: { ...DEFAULT_SERIAL_PROMPTS }
      })
    ).toEqual([])
  })

  it('requires a serial port name and port 0', () => {
    const errors = validateSerialConnection({
      host: '  ',
      port: 22,
      serialParams: { ...DEFAULT_SERIAL_PARAMS },
      serialPrompts: { ...DEFAULT_SERIAL_PROMPTS }
    })
    expect(errors.some((message) => message.includes('串口名称'))).toBe(true)
    expect(errors.some((message) => message.includes('端口必须为 0'))).toBe(true)
  })

  it('requires serial params and prompts', () => {
    const errors = validateSerialConnection({ host: 'COM3', port: 0 })
    expect(errors.some((message) => message.includes('串口参数'))).toBe(true)
    expect(errors.some((message) => message.includes('串口提示符'))).toBe(true)
  })

  it('validates baud rate, data bits and stop bits against backend ranges', () => {
    const base = { ...DEFAULT_SERIAL_PARAMS }
    expect(
      validateSerialConnection({
        host: 'COM3',
        port: 0,
        serialParams: { ...base, baudRate: 0 },
        serialPrompts: { ...DEFAULT_SERIAL_PROMPTS }
      }).some((message) => message.includes('波特率'))
    ).toBe(true)
    expect(
      validateSerialConnection({
        host: 'COM3',
        port: 0,
        serialParams: { ...base, dataBits: 9 },
        serialPrompts: { ...DEFAULT_SERIAL_PROMPTS }
      }).some((message) => message.includes('数据位'))
    ).toBe(true)
    expect(
      validateSerialConnection({
        host: 'COM3',
        port: 0,
        serialParams: { ...base, stopBits: 3 },
        serialPrompts: { ...DEFAULT_SERIAL_PROMPTS }
      }).some((message) => message.includes('停止位'))
    ).toBe(true)
    expect(
      validateSerialConnection({
        host: 'COM3',
        port: 0,
        serialParams: { ...base, parity: 'ODD' },
        serialPrompts: { ...DEFAULT_SERIAL_PROMPTS }
      }).some((message) => message.includes('校验'))
    ).toBe(false)
  })

  it('requires non-empty prompts', () => {
    const errors = validateSerialConnection({
      host: 'COM3',
      port: 0,
      serialParams: { ...DEFAULT_SERIAL_PARAMS },
      serialPrompts: { login: '', password: '', command: '', lineEnding: 'AUTO' }
    })
    expect(errors.some((message) => message.includes('登录名提示符'))).toBe(true)
    expect(errors.some((message) => message.includes('密码提示符'))).toBe(true)
    expect(errors.some((message) => message.includes('命令提示符'))).toBe(true)
  })
})
