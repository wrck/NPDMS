export type PlanningSection = 'interface' | 'ip'

export const planningColumns: Record<PlanningSection, readonly (readonly [string, string])[]> = {
  interface: [
    ['localDevice', '本端设备'], ['localPort', '本端接口'], ['peerPort', '对端接口'],
    ['portType', '接口类型'], ['cableModel', '线缆/光模块型号'], ['remark', '备注']
  ],
  ip: [
    ['deviceName', '设备名称'], ['port', '接口'], ['ip', 'IP地址'], ['mask', '掩码'],
    ['vrrp', 'VRRP地址'], ['gateway', '网关'], ['remark', '备注']
  ]
} as const

export const planningTemplate = (section: PlanningSection) =>
  '\uFEFF' + planningColumns[section].map(([, label]) => label).join(',') + '\r\n'

// Keep every cell as text (including leading zeroes); parse quoted commas/newlines before applying any rows.
export const parsePlanningImport = (section: PlanningSection, source: string): Record<string, string>[] => {
  const text = source.replace(/^\uFEFF/, '')
  const rows: string[][] = []
  let row: string[] = [], cell = '', quoted = false, closed = false
  const pushCell = () => { row.push(cell); cell = ''; closed = false }
  const pushRow = () => { pushCell(); rows.push(row); row = [] }
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (quoted) {
      if (ch !== '"') cell += ch
      else if (text[i + 1] === '"') { cell += '"'; i++ }
      else { quoted = false; closed = true }
    } else if (ch === ',') pushCell()
    else if (ch === '\r' || ch === '\n') {
      if (ch === '\r' && text[i + 1] === '\n') i++
      pushRow()
    } else if (ch === '"' && !cell && !closed) quoted = true
    else {
      if (closed || ch === '"') throw new Error('CSV引号格式错误，请使用模板并另存为CSV UTF-8。')
      cell += ch
    }
  }
  if (quoted) throw new Error('CSV引号未闭合，请检查文件。')
  if (cell || row.length || closed) pushRow()
  return parsePlanningRows({ section, rows })
}

export const parsePlanningRows = ({ section, rows: sourceRows, rowNumbers }: {
  section: PlanningSection; rows: string[][]; rowNumbers?: number[]
}): Record<string, string>[] => {
  const rows = [...sourceRows]
  const columns = planningColumns[section]
  const header = rows.shift()
  if (!header || header.length !== columns.length || columns.some(([, label], i) => header[i].trim() !== label)) {
    throw new Error('表头与当前规划不一致，请下载对应模板，保留列名和顺序。')
  }
  const result = rows.flatMap((values, index) => {
    if (values.every(value => !value.trim())) return []
    if (values.length !== columns.length) throw new Error(`第${rowNumbers?.[index + 1] ?? index + 2}行列数不正确，本次未导入任何行。`)
    return [Object.fromEntries(columns.map(([key], i) => [key, values[i]]))]
  })
  if (!result.length) throw new Error('文件没有可导入的数据行。')
  return result
}
