export interface SerialParams {
  baudRate: number
  dataBits: number
  parity: 'NONE' | 'EVEN' | 'ODD' | 'MARK' | 'SPACE'
  stopBits: number
  flowControl: 'NONE' | 'RTS_CTS' | 'XON_XOFF'
}

export interface SerialPrompts {
  login: string
  password: string
  command: string
  lineEnding: 'AUTO' | 'CRLF' | 'CR' | 'LF'
}

export const SERIAL_BAUD_RATES = [4800, 9600, 19200, 38400, 57600, 115200] as const

export const SERIAL_PARITY_OPTIONS = [
  { value: 'NONE', label: '无校验' },
  { value: 'EVEN', label: '偶校验' },
  { value: 'ODD', label: '奇校验' },
  { value: 'MARK', label: 'MARK' },
  { value: 'SPACE', label: 'SPACE' }
] as const

export const SERIAL_STOP_BIT_OPTIONS = [
  { value: 1, label: '1' },
  { value: 2, label: '2' }
] as const

export const SERIAL_FLOW_CONTROL_OPTIONS = [
  { value: 'NONE', label: '无流控' },
  { value: 'RTS_CTS', label: 'RTS/CTS' },
  { value: 'XON_XOFF', label: 'XON/XOFF' }
] as const

export const SERIAL_LINE_ENDING_OPTIONS = [
  { value: 'AUTO', label: '自动' },
  { value: 'CRLF', label: 'CRLF' },
  { value: 'CR', label: 'CR' },
  { value: 'LF', label: 'LF' }
] as const

export const DEFAULT_SERIAL_PARAMS: SerialParams = {
  baudRate: 9600,
  dataBits: 8,
  parity: 'NONE',
  stopBits: 1,
  flowControl: 'NONE'
}

export const DEFAULT_SERIAL_PROMPTS: SerialPrompts = {
  login: '(?i)(login|username)\\s*:\\s*$',
  password: '(?i)password\\s*:\\s*$',
  command: '[>#\\$]\\s*$',
  lineEnding: 'AUTO'
}

export function serialParamFields(): Array<keyof SerialParams> {
  return ['baudRate', 'dataBits', 'parity', 'stopBits', 'flowControl']
}

export interface SerialConnectionDraft {
  host: string
  port: number
  serialParams?: SerialParams | null
  serialPrompts?: SerialPrompts | null
}

const PARITY_VALUES = SERIAL_PARITY_OPTIONS.map((option) => option.value)
const FLOW_CONTROL_VALUES = SERIAL_FLOW_CONTROL_OPTIONS.map((option) => option.value)
const LINE_ENDING_VALUES = SERIAL_LINE_ENDING_OPTIONS.map((option) => option.value)

export function validateSerialConnection(draft: SerialConnectionDraft): string[] {
  const errors: string[] = []
  if (!draft.host || !draft.host.trim()) {
    errors.push('请输入串口名称（如 COM3）。')
  }
  if (draft.port !== 0) {
    errors.push('串口连接的端口必须为 0。')
  }
  const params = draft.serialParams
  if (!params) {
    errors.push('请填写串口参数。')
  } else {
    if (!Number.isInteger(params.baudRate) || params.baudRate <= 0) {
      errors.push('波特率必须为正整数。')
    }
    if (!Number.isInteger(params.dataBits) || params.dataBits < 5 || params.dataBits > 8) {
      errors.push('数据位必须为 5–8。')
    }
    if (params.stopBits !== 1 && params.stopBits !== 2) {
      errors.push('停止位必须为 1 或 2。')
    }
    if (!PARITY_VALUES.includes(params.parity)) {
      errors.push('校验位取值无效。')
    }
    if (!FLOW_CONTROL_VALUES.includes(params.flowControl)) {
      errors.push('流控取值无效。')
    }
  }
  const prompts = draft.serialPrompts
  if (!prompts) {
    errors.push('请填写串口提示符。')
  } else {
    if (!prompts.login?.trim()) {
      errors.push('请输入登录名提示符。')
    }
    if (!prompts.password?.trim()) {
      errors.push('请输入密码提示符。')
    }
    if (!prompts.command?.trim()) {
      errors.push('请输入命令提示符。')
    }
    if (!LINE_ENDING_VALUES.includes(prompts.lineEnding)) {
      errors.push('换行方式取值无效。')
    }
  }
  return errors
}
