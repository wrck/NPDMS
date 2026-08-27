import { generateUUID } from '@/utils'
import { makeRequiredRule } from '@/components/FormCreate/src/utils'

export const usePmsFileArtifactDesignerRule = () => ({
  icon: 'icon-upload',
  label: '受控文件材料',
  name: 'PmsFileArtifact',
  rule: () => ({
    type: 'PmsFileArtifact',
    field: generateUUID(),
    title: '受控文件材料',
    info: '文件由平台文件事实管理，不写入普通表单值。',
    $required: false
  }),
  props: () => [makeRequiredRule()]
})
