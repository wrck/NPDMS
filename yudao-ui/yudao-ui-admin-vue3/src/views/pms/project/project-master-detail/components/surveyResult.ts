import type { SurveyResult } from '@/api/pms/engineering/preparation'

export type SurveyTextKey =
  | 'powerSupply'
  | 'powerEnvironment'
  | 'networkPort'
  | 'fiber'
  | 'cabinet'
  | 'networkCable'
  | 'opticalModule'
export type SurveyBooleanKey =
  | 'cabinetAvailable'
  | 'networkCableAvailable'
  | 'opticalModuleAvailable'
  | 'originalOpticalModule'
export interface SurveyField {
  key: SurveyTextKey | SurveyBooleanKey
  label: string
  kind: 'text' | 'boolean'
  placeholder?: string
}

const fields: Record<string, SurveyField[]> = {
  POWER: [
    {
      key: 'powerSupply',
      label: '供电条件',
      kind: 'text',
      placeholder: '例如：交流 / 直流、供电容量及现场确认情况'
    },
    {
      key: 'powerEnvironment',
      label: '供电环境',
      kind: 'text',
      placeholder: '插头标准、接入条件及需要注意的限制'
    }
  ],
  NETWORK_PORT: [
    {
      key: 'networkPort',
      label: '网络端口',
      kind: 'text',
      placeholder: '接口类型、速率、数量及现场可用情况'
    }
  ],
  FIBER: [
    {
      key: 'fiber',
      label: '光纤条件',
      kind: 'text',
      placeholder: '光纤类型、连接距离、接口和现场条件'
    }
  ],
  CABINET: [
    { key: 'cabinetAvailable', label: '机柜资源是否具备', kind: 'boolean' },
    {
      key: 'cabinet',
      label: '机柜条件说明',
      kind: 'text',
      placeholder: '空间、承重、散热及需要落实的事项'
    }
  ],
  NETWORK_CABLE: [
    { key: 'networkCableAvailable', label: '网线资源是否具备', kind: 'boolean' },
    {
      key: 'networkCable',
      label: '网线条件说明',
      kind: 'text',
      placeholder: '线缆规格、数量、走线及缺口'
    }
  ],
  OPTICAL_MODULE: [
    { key: 'opticalModuleAvailable', label: '光模块资源是否具备', kind: 'boolean' },
    { key: 'originalOpticalModule', label: '是否使用原厂光模块', kind: 'boolean' },
    {
      key: 'opticalModule',
      label: '光模块条件说明',
      kind: 'text',
      placeholder: '型号、规格、适配情况及缺口'
    }
  ]
}

export const surveyFields = (itemCode: string): SurveyField[] => fields[itemCode] || []

export const normalizeSurveyResult = (itemCode: string, value: SurveyResult): SurveyResult => {
  const result: SurveyResult = {}
  for (const field of surveyFields(itemCode)) {
    if (field.kind === 'boolean') {
      const key = field.key as SurveyBooleanKey
      result[key] = typeof value[key] === 'boolean' ? value[key] : null
    } else {
      const key = field.key as SurveyTextKey
      result[key] = value[key]?.trim() || null
    }
  }
  return result
}

export const surveySummary = (itemCode: string, result?: SurveyResult | null): string => {
  if (!result) return '尚未填写业务内容'
  return (
    surveyFields(itemCode)
      .flatMap((field) => {
        const value = result[field.key]
        if (value === null || value === undefined || value === '') return []
        return [`${field.label}：${typeof value === 'boolean' ? (value ? '是' : '否') : value}`]
      })
      .join('；') || '尚未填写业务内容'
  )
}
