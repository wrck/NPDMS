import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey/entity'

const escape = (value: unknown) => String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
const text = (value: unknown) => String(value ?? '').replace(/<[^>]+>/g, ' ').trim()
const resourceRow = ({ label, value }: { label: string; value: unknown }) => text(value) ? `<tr><td>${escape(label)}</td><td>${escape(text(value))}</td></tr>` : ''
const section = ({ title, rows }: { title: string; rows: { label: string; value: unknown }[] }) => {
  const content = rows.map(resourceRow).join('')
  return content ? `<h3>${escape(title)}</h3><table border="1" style="width:100%;table-layout:fixed;border-collapse:collapse"><thead><tr><th>确认项</th><th>现状/确认内容</th></tr></thead><tbody>${content}</tbody></table>` : ''
}

export const requirementResources = (values: Record<string, unknown>): string => {
  const transmission = section({ title: '传输现状', rows: [
    { label: '传输类型', value: Array.isArray(values.transmissionCurrentOptions) ? [...new Set(values.transmissionCurrentOptions)].join('、') : '' },
    { label: '现状说明', value: values.transmissionRequirement }
  ] })
  const traffic = section({ title: '流量现状', rows: [
    { label: '新建', value: values.trafficNewConnections }, { label: '并发', value: values.trafficConcurrency },
    { label: '吞吐', value: values.trafficThroughput }, { label: '现状说明', value: values.trafficRequirement }
  ] })
  const rows = Array.isArray(values.businessDeviceDetails) ? values.businessDeviceDetails as Record<string, unknown>[] : []
  const columns = [['deviceName', '设备名称'], ['serialNumber', '序列号'], ['businessName', '承载业务名称'], ['businessSubnet', '业务网段'], ['businessImportance', '业务重要等级'], ['interfaces', '出入接口'], ['customerBusinessOwner', '客户侧业务负责人'], ['remark', '备注']]
  const uniqueRows = [...new Map(rows.map(row => [JSON.stringify(columns.map(([key]) => String(row[key] ?? '').trim())), row])).values()]
  const table = uniqueRows.length ? `<table border="1" style="width:100%;table-layout:fixed;border-collapse:collapse"><thead><tr>${columns.map(([, label]) => `<th>${escape(label)}</th>`).join('')}</tr></thead><tbody>${uniqueRows.map(row => `<tr>${columns.map(([key]) => `<td>${escape(row[key])}</td>`).join('')}</tr>`).join('')}</tbody></table>` : ''
  const description = resourceRow({ label: '业务说明', value: values.businessRequirement })
  const business = table || description ? `<h3>运行业务情况</h3>${table}${description ? `<table border="1" style="width:100%;table-layout:fixed;border-collapse:collapse"><tbody>${description}</tbody></table>` : ''}` : ''
  return `${transmission}${traffic}${business}`
}

export const surveyResources = (survey: SiteSurveyVO): string => {
  const columns = [['location', '工勘地点'], ['powerSupply', '供电'], ['cabinet', '机柜'], ['networkPort', '网络端口'], ['fiber', '光纤'], ['module', '模块'], ['cable', '线缆'], ['ground', '接地'], ['constructionResource', '施工资源'], ['conclusion', '工勘结论']]
  return section({ title: `工勘资源确认：${survey.name}${survey.code ? `（${survey.code}）` : ''}`,
    rows: columns.map(([key, label]) => ({ label, value: survey[key as keyof SiteSurveyVO] })) })
}
