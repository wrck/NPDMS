import formCreate from '@form-create/element-ui'
import PmsFileArtifactField from './PmsFileArtifactField.vue'
import RequirementBusinessTable from '@/views/pms/delivery-business/requirement-analysis/entity/RequirementBusinessTable.vue'
import RequirementLoggingChoices from '@/views/pms/delivery-business/requirement-analysis/entity/RequirementLoggingChoices.vue'
import '@/views/pms/delivery-business/requirement-analysis/requirementTemplate.scss'

let registered = false

export const registerDynamicFormComponents = () => {
  if (registered) return
  formCreate.component('PmsFileArtifact', PmsFileArtifactField)
  formCreate.component('RequirementBusinessTable', RequirementBusinessTable)
  formCreate.component('RequirementLoggingChoices', RequirementLoggingChoices)
  registered = true
}
