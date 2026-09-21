export const parserExamples = {
  JSON: {
    description: '适用于命令输出本身就是一个 JSON 对象；解析配置留空。',
    input: '{"sn":"SN-001","softVersion":"1.2.3"}',
    output: '{"sn":"SN-001","softVersion":"1.2.3"}',
    config: ''
  },
  KEY_VALUE: {
    description: '适用于每行一个键值对。separator 指定分隔符，ignoreBlankLines 控制是否忽略空行。',
    input: 'sn=SN-001\nsoftVersion=1.2.3',
    output: '{"sn":"SN-001","softVersion":"1.2.3"}',
    config: '{"separator":"=","ignoreBlankLines":true}'
  }
} as const

export function parserConfigError(type: 'NONE' | 'JSON' | 'KEY_VALUE', config?: string): string {
  if (type !== 'KEY_VALUE' || !config?.trim()) return ''
  try {
    const value = JSON.parse(config) as { separator?: unknown, ignoreBlankLines?: unknown }
    if (!value || typeof value !== 'object' || typeof value.separator !== 'string' || !value.separator) {
      return 'separator 必须是非空字符串。'
    }
    if (value.ignoreBlankLines != null && typeof value.ignoreBlankLines !== 'boolean') {
      return 'ignoreBlankLines 必须是 true 或 false。'
    }
    return ''
  } catch {
    return '解析配置必须是合法 JSON。'
  }
}
