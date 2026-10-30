import type { CollectionDetails } from '@/types/collection'
import type { InputContentStatus } from '@/types/collection-evidence'
import { blocksForTarget, renderCommandTranscript } from '@/utils/command-output-blocks'

function timestamp(date: Date): string {
  const part = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${part(date.getMonth() + 1)}${part(date.getDate())}-${part(date.getHours())}${part(date.getMinutes())}${part(date.getSeconds())}`
}

export function safeExportName(device: string, suffix: '_input.txt' | '_session.log'): string {
  const stamp = timestamp(new Date())
  const tail = `_${stamp}${suffix}`
  const maxDeviceLength = Math.max(1, 80 - tail.length)
  const safeDevice =
    device
      .normalize('NFKC')
      // Control characters are intentionally rejected from Windows export filenames.
      // eslint-disable-next-line no-control-regex
      .replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_')
      .replace(/[. ]+$/g, '')
      .trim()
      .slice(0, maxDeviceLength) || 'device'
  return `${safeDevice}${tail}`
}

function downloadUtf8(filename: string, content: string): void {
  const blob = new Blob(['\uFEFF', content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function downloadInputRecord(device: string, script: string): void {
  downloadUtf8(safeExportName(device, '_input.txt'), script)
}

export function downloadSessionRecord(
  device: string,
  script: string,
  details: CollectionDetails,
  submittedAt: string,
  inputStatus: InputContentStatus = script.length ? 'AVAILABLE' : 'UNAVAILABLE',
  createdAt?: string | null
): void {
  const targets = details.targets
  const sessionLines = [
    `collectionId=${details.collectionId}`,
    `namespace=${details.namespace}`,
    `status=${details.status}`,
    `submittedAt=${submittedAt || 'unknown'}`,
    `createdAt=${createdAt ?? 'unknown'}`,
    `inputStatus=${inputStatus}`,
    `savedAt=${new Date().toISOString()}`,
    ...targets.flatMap((target, index) => [
      `target.${index + 1}.protocol=${target.endpointSnapshot.protocol}`,
      `target.${index + 1}.endpoint=${target.endpointSnapshot.username}@${target.endpointSnapshot.host}:${target.endpointSnapshot.port}`,
      `target.${index + 1}.status=${target.status}`,
      `target.${index + 1}.outcome=${target.outcome ?? ''}`,
      `target.${index + 1}.facts=${JSON.stringify(target.parsedFacts)}`
    ])
  ]
  const commandOutput = targets
    .map((target, index) =>
      `--- target ${index + 1} ---\n${renderCommandTranscript(blocksForTarget(target))}`
    )
    .join('\n\n')
  const content = [
    '[SESSION]',
    ...sessionLines,
    '',
    '[INPUT]',
    inputStatus === 'AVAILABLE' ? script : inputStatus === 'RESTRICTED' ? '证据访问受限（RESTRICTED）。' : '原始脚本不可用（UNAVAILABLE）。',
    '',
    '[COMMAND OUTPUT]',
    commandOutput
  ].join('\n')
  downloadUtf8(safeExportName(device, '_session.log'), content)
}
