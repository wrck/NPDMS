<template>
  <el-dialog v-model="visible" :title="`${row?.name ?? ''} · 交付判定规则`" width="min(760px, 94vw)" append-to-body>
    <el-form v-if="row" label-position="top" :disabled="readonly">
      <el-form-item label="允许的材料来源">
        <el-checkbox-group v-model="allowedSources">
          <el-checkbox value="UPLOAD">上传文件</el-checkbox>
          <el-checkbox value="BUSINESS_RESULT">关联业务成果</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="最少有效材料数量"><el-input-number v-model="minimumQuantity" :min="row.required ? 1 : 0" :max="100" :precision="0" /></el-form-item>
      <el-form-item label="自动归集业务文档">
        <el-select v-model="automaticSources" multiple clearable :teleported="false" :loading="sourcesLoading" placeholder="选择业务文档来源" aria-label="自动归集业务文档">
          <el-option v-for="source in sourceOptions" :key="source.code" :value="source.code" :label="source.name" />
        </el-select>
      </el-form-item>
      <el-alert v-if="legacyRule" title="此版本原有额外判定条件。保存后改为按有效材料存在及数量判定，不再等待任务完成；已发布版本和项目历史不变。" type="warning" :closable="false" />
      <p>有效交付文件或配置来源文档存在、数量达标，即满足交付件条件。任务完成与阶段推进仍由各自规则决定。</p>
    </el-form>
    <template #footer><el-button @click="visible = false">取消</el-button><el-button v-if="!readonly" type="primary" @click="save">保存判定规则</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import type { JsonObject, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import { constantRule } from './versionRuleModel'
import { getDocumentSources } from '@/api/pms/acceptance/project-deliverable'
defineProps<{ document: TemplateDesignerDocument; readonly?: boolean }>()
const visible = ref(false)
const row = ref<TemplateDesignerDocument['deliverables'][number]>()
let configuration: JsonObject = {}
const allowedSources = ref<string[]>([])
const minimumQuantity = ref(1)
const automaticSources = ref<string[]>([]), sourceOptions = ref<{ code: string; name: string }[]>([])
const sourcesLoading = ref(false), legacyRule = ref(false)
const open = async (item: TemplateDesignerDocument['deliverables'][number]) => {
  row.value = item
  configuration = item.configuration ? JSON.parse(JSON.stringify(item.configuration)) : {
    scope: item.taskCode ? 'TASK' : 'STAGE', deliverableType: 'DOCUMENT', outputType: 'FILE',
    required: !!item.required, minimumQuantity: 1, allowedSources: ['UPLOAD'],
    confirmationRule: constantRule(true)
  }
  allowedSources.value = (configuration.allowedSources as string[]) || []
  minimumQuantity.value = Number(configuration.minimumQuantity ?? 1)
  automaticSources.value = [...((configuration.automaticSources as string[]) || [])]
  legacyRule.value = JSON.stringify(configuration.confirmationRule) !== JSON.stringify(constantRule(true))
  visible.value = true
  sourcesLoading.value = true
  try { sourceOptions.value = await getDocumentSources() } finally { sourcesLoading.value = false }
}
const save = () => {
  if (!row.value) return
  row.value.configuration = { ...configuration, allowedSources: [...allowedSources.value], minimumQuantity: minimumQuantity.value,
    confirmationRule: constantRule(true), automaticSources: [...automaticSources.value], required: !!row.value.required,
    scope: row.value.taskCode ? 'TASK' : 'STAGE', outputType: allowedSources.value.includes('BUSINESS_RESULT') ? 'FILE_OR_BUSINESS_RESULT' : 'FILE' }
  visible.value = false
}
defineExpose({ open })
</script>
