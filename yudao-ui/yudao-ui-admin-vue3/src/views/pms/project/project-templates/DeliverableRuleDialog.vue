<template>
  <el-dialog v-model="visible" :title="`${row?.name ?? ''} · 交付判定规则`" width="min(760px, 94vw)">
    <el-form v-if="row" label-position="top" :disabled="readonly">
      <el-form-item label="允许的材料来源">
        <el-checkbox-group v-model="allowedSources">
          <el-checkbox value="UPLOAD">上传文件</el-checkbox>
          <el-checkbox value="BUSINESS_RESULT">关联业务成果</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="最少有效材料数量"><el-input-number v-model="minimumQuantity" :min="row.required ? 1 : 0" :max="100" :precision="0" /></el-form-item>
      <el-form-item label="自动判定条件">
        <RuleDecisionDesigner v-model="confirmationRule" :rules="document.rules" :disabled="readonly" />
      </el-form-item>
      <p>文件或业务成果有效、数量达标，且以上条件满足时，交付件自动满足门禁。</p>
    </el-form>
    <template #footer><el-button @click="visible = false">取消</el-button><el-button v-if="!readonly" type="primary" @click="save">保存判定规则</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, shallowRef } from 'vue'
import type { JsonObject, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import RuleDecisionDesigner from './RuleDecisionDesigner.vue'
import { constantRule } from './versionRuleModel'
defineProps<{ document: TemplateDesignerDocument; readonly?: boolean }>()
const visible = ref(false)
const row = ref<TemplateDesignerDocument['deliverables'][number]>()
let configuration: JsonObject = {}
const allowedSources = ref<string[]>([])
const minimumQuantity = ref(1)
const confirmationRule = shallowRef<JsonObject>(constantRule(false))
const open = (item: TemplateDesignerDocument['deliverables'][number]) => {
  row.value = item
  configuration = item.configuration ? JSON.parse(JSON.stringify(item.configuration)) : {
    scope: item.taskCode ? 'TASK' : 'STAGE', deliverableType: 'DOCUMENT', outputType: 'FILE',
    required: !!item.required, minimumQuantity: 1, allowedSources: ['UPLOAD'],
    confirmationRule: item.taskCode ? { predicate: 'TASK', parameters: { refCode: item.taskCode } } : constantRule(false)
  }
  allowedSources.value = (configuration.allowedSources as string[]) || []
  minimumQuantity.value = Number(configuration.minimumQuantity ?? 1)
  confirmationRule.value = configuration.confirmationRule as JsonObject
  visible.value = true
}
const save = () => {
  if (!row.value) return
  row.value.configuration = { ...configuration, allowedSources: [...allowedSources.value], minimumQuantity: minimumQuantity.value,
    confirmationRule: JSON.parse(JSON.stringify(confirmationRule.value)), required: !!row.value.required,
    scope: row.value.taskCode ? 'TASK' : 'STAGE', outputType: allowedSources.value.includes('BUSINESS_RESULT') ? 'FILE_OR_BUSINESS_RESULT' : 'FILE' }
  visible.value = false
}
defineExpose({ open })
</script>
