import type { JsonObject } from '@/api/pms/platform/dynamic-form'
import type { TrainingVO } from '@/api/pms/engineering/training'
import dayjs from 'dayjs'

export const PRINT_CATEGORY = 'TRAINING_PRINT_FORM'
export const PRINT_ENGINE = 'FORM_CREATE_ELEMENT_PLUS'
export interface PrintSnapshot {
  engine: string
  formConfJson: JsonObject
  formRulesJson: JsonObject[]
}

// The approved sample is a native FormCreate table layout, editable in the existing designer.
export const APPROVED_PRINT_CODE = 'TRAINING_PRINT_APPROVED'
export const createTrainingPrintForm = (types: { label: string; value: string }[]) => {
  const input = (field: string, title: string, slot: string, multiline = false): JsonObject => ({
    type: 'input',
    field,
    title,
    slot,
    props: multiline ? { type: 'textarea', autosize: { minRows: 2 } } : {}
  })
  const title = (text: string, slot?: string): JsonObject => ({
    type: 'fcTitle',
    slot,
    props: { title: text, size: 'h5' }
  })
  const rating = (field: string, slot: string, labels: string[]): JsonObject => ({
    type: 'radio',
    field,
    title: '',
    slot,
    options: labels.map((label) => ({ label, value: label }))
  })
  const table = (
    row: number,
    col: number,
    layout: object[],
    style: object,
    children: JsonObject[]
  ): JsonObject => ({
    type: 'fcTable',
    props: {
      border: true,
      borderColor: '#cbd5e1',
      borderWidth: '1px',
      rule: { row, col, layout, style, class: {} }
    },
    children
  })
  return {
    formConfJson: {
      trainingPrintStyle: 'APPROVED_TABLE',
      form: { labelPosition: 'top', labelWidth: 'auto', size: 'default' },
      submitBtn: false,
      resetBtn: false
    },
    formRulesJson: [
      { type: 'fcTitle', props: { title: '现场培训记录表', align: 'center', size: 'h2' } },
      { ...title('培训记录填写'), class: 'training-print-section' },
      { type: 'input', field: 'name', title: '培训名称', hidden: true },
      table(
        3,
        3,
        [{ top: 2, left: 0, row: 1, col: 3 }],
        {
          '0:0': { width: '44%', height: '76px' },
          '0:1': { width: '26%' },
          '0:2': { width: '30%' },
          '1:0': { height: '76px' },
          '2:0': { height: '154px' }
        },
        [
          input('projectName', '工程名称', '0:0'),
          input('contactName', '客户联系人', '0:1'),
          input('contactPhone', '客户联系电话', '0:2'),
          {
            type: 'checkbox',
            field: 'trainingTypes',
            title: '培训类型',
            slot: '1:0',
            options: types
          },
          input('trainingTime', '培训时间', '1:1'),
          input('trainerName', '培训工程师', '1:2'),
          input('content', '培训内容', '2:0', true)
        ]
      ),
      { ...title('客户确认信息'), class: 'training-print-section' },
      table(
        5,
        3,
        [
          { top: 0, left: 0, row: 3, col: 1 },
          { top: 3, left: 1, row: 1, col: 2 },
          { top: 4, left: 0, row: 1, col: 2 }
        ],
        {
          '0:0': { width: '14%', verticalAlign: 'middle', background: '#f3f6fa' },
          '0:1': { width: '43%', height: '48px', verticalAlign: 'middle' },
          '0:2': { width: '43%', verticalAlign: 'middle' },
          '1:1': { height: '48px', verticalAlign: 'middle' },
          '1:2': { verticalAlign: 'middle' },
          '2:1': { height: '48px', verticalAlign: 'middle' },
          '2:2': { verticalAlign: 'middle' },
          '3:0': { height: '100px', verticalAlign: 'middle', background: '#f3f6fa' },
          '4:0': { height: '96px', borderRight: 'none' },
          '4:2': { borderLeft: 'none' }
        },
        [
          title('客户意见', '0:0'),
          title('培训工程师技术水平及表达能力', '0:1'),
          rating('skillRating', '0:2', ['很好', '良好', '一般', '差']),
          title('培训内容及讲解效果', '1:1'),
          rating('effectRating', '1:2', ['很好', '良好', '一般', '差']),
          title('培训满意度', '2:1'),
          rating('satisfactionRating', '2:2', ['非常满意', '较满意', '一般', '差']),
          title('综合意见', '3:0'),
          input('signOpinion', '', '3:1', true),
          {
            type: 'signaturePad',
            field: 'signatureImageDataUrl',
            title: '签字',
            slot: '4:0',
            col: { span: 24 }
          },
          { ...input('signTime', '日期', '4:2'), col: { span: 24 } },
          { ...input('signConfirmerName', '签字人', '4:0'), col: { span: 24 } }
        ]
      )
    ] as JsonObject[]
  }
}

export const trainingPrintValues = (record: TrainingVO, projectName: string) => ({
  ...(record.confirmationValues ? JSON.parse(record.confirmationValues) : {}),
  ...record,
  projectName,
  signTime: record.signTime ? dayjs(record.signTime).format('YYYY-MM-DD HH:mm') : '',
  trainingTypes: (record.trainingTypes || '').split(',').filter(Boolean)
})
