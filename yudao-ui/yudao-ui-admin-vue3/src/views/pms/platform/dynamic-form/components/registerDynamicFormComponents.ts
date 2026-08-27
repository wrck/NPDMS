import formCreate from '@form-create/element-ui'
import PmsFileArtifactField from './PmsFileArtifactField.vue'

let registered = false

export const registerDynamicFormComponents = () => {
  if (registered) return
  formCreate.component('PmsFileArtifact', PmsFileArtifactField)
  registered = true
}
