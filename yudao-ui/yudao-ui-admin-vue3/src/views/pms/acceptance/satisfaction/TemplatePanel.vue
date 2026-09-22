<template>
  <el-form inline class="-mb-15px">
    <el-form-item>
      <el-button :loading="loading" @click="load"><Icon icon="ep:search" />查询</el-button>
      <el-button type="primary" @click="openCreate"><Icon icon="ep:plus" />新建模板</el-button>
    </el-form-item>
  </el-form>
  <el-skeleton v-if="loading" :rows="4" animated />
  <el-empty v-else-if="!templates.length" description="暂无问卷模板" />
  <el-table v-else :data="templates" stripe>
    <el-table-column prop="templateCode" label="模板编码" min-width="160" />
    <el-table-column prop="name" label="名称" min-width="180" />
    <el-table-column label="状态" width="120"><template #default="{ row }">{{ { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' }[row.status] || row.status }}</template></el-table-column>
    <el-table-column label="当前修订" width="110">
      <template #default="scope">{{ currentRevision(scope.row)?.revisionNo ?? '—' }}</template>
    </el-table-column>
    <el-table-column label="操作" width="230" fixed="right">
      <template #default="scope">
        <el-button link type="primary" @click="openRevision(scope.row)">新建修订</el-button>
        <el-button v-if="draftRevision(scope.row)" link type="success" @click="publish(scope.row)"
          >发布草稿</el-button
        >
      </template>
    </el-table-column>
  </el-table>

  <el-dialog
    v-model="dialogVisible"
    :title="mode === 'template' ? '新建模板' : '新建模板修订'"
    width="min(960px, 96vw)"
    :close-on-click-modal="false"
  >
    <el-form label-position="top" :disabled="saving">
      <template v-if="mode === 'template'">
        <el-form-item label="模板编码"
          ><el-input v-model="templateForm.templateCode"
        /></el-form-item>
        <el-form-item label="模板名称"><el-input v-model="templateForm.name" /></el-form-item>
      </template>
      <template v-else>
        <div class="dimension-grid">
          <el-form-item label="项目类型"
            ><el-input v-model="revisionForm.projectType"
          /></el-form-item>
          <el-form-item label="签约模式"
            ><el-input v-model="revisionForm.signingMode"
          /></el-form-item>
          <el-form-item label="实施模式"
            ><el-input v-model="revisionForm.implementationMode"
          /></el-form-item>
          <el-form-item label="业务用途"
            ><el-input v-model="revisionForm.businessPurposeCode"
          /></el-form-item>
          <el-form-item label="触发时点"
            ><el-input v-model="revisionForm.applicableTimingCode"
          /></el-form-item>
          <el-form-item label="优先级"
            ><el-input-number v-model="revisionForm.priority" :min="0"
          /></el-form-item>
        </div>
        <QuestionnaireDesigner v-model="designer" />
        <div class="dimension-grid">
          <el-form-item label="阈值"><el-input v-model="revisionForm.threshold" /></el-form-item>
          <el-form-item label="规则版本"
            ><el-input v-model="revisionForm.ruleVersion"
          /></el-form-item>
        </div>
        <el-alert v-if="designError" :title="designError" type="warning" :closable="false" />
        <template v-else>
          <p>问卷满分：{{ previewMaximum }}</p>
          <el-collapse><el-collapse-item title="预览客户问卷" name="preview">
            <QuestionnaireFields :frozen-questions="previewJson" />
          </el-collapse-item></el-collapse>
        </template>
      </template>
    </el-form>
    <template #footer
      ><el-button :disabled="saving" @click="dialogVisible = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import * as Api from '@/api/pms/acceptance/satisfaction'
import type { TemplateRevision, TemplateView } from '@/api/pms/acceptance/satisfaction'
import QuestionnaireDesigner from './QuestionnaireDesigner.vue'
import QuestionnaireFields from './QuestionnaireFields.vue'
import { emptyDesigner, readDesigner, writeDesigner } from './questionnaireDesigner'

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const templates = ref<TemplateView[]>([])
const dialogVisible = ref(false)
const mode = ref<'template' | 'revision'>('template')
const selected = ref<TemplateView>()
const templateForm = reactive({ templateCode: '', name: '' })
const designer = ref(emptyDesigner())
const revisionForm = reactive({
  projectType: '',
  signingMode: '',
  implementationMode: '',
  businessPurposeCode: '',
  applicableTimingCode: 'AFTER_INITIAL_ACCEPTANCE',
  priority: 100,
  threshold: '80.00',
  ruleVersion: 'SAT-RULE-V1'
})
const design = computed(() => {
  try { return { json: writeDesigner(designer.value, revisionForm.ruleVersion, revisionForm.threshold), error: '' } }
  catch (error) { return { json: '', error: error instanceof Error ? error.message : '问卷配置无效' } }
})
const previewJson = computed(() => design.value.json)
const designError = computed(() => design.value.error)
const previewMaximum = computed(() => previewJson.value ? JSON.parse(previewJson.value).scoring.scoreMax : '')

const currentRevision = (row: TemplateView) =>
  row.revisions.find((item) => item.id === row.currentRevisionId)
const draftRevision = (row: TemplateView) => row.revisions.filter((item) => item.status === 'DRAFT'
    && item.revisionNo > (currentRevision(row)?.revisionNo ?? 0))
  .sort((a, b) => b.revisionNo - a.revisionNo)[0]
const load = async () => {
  loading.value = true
  try {
    templates.value = await Api.listTemplates()
  } finally {
    loading.value = false
  }
}
const openCreate = () => {
  mode.value = 'template'
  templateForm.templateCode = ''
  templateForm.name = ''
  dialogVisible.value = true
}
const openRevision = (row: TemplateView) => {
  const source = draftRevision(row) || currentRevision(row)
  try { designer.value = source ? readDesigner(source.questionnaireJson) : emptyDesigner() }
  catch { message.warning('现有问卷配置无法读取，未覆盖原配置'); return }
  Object.assign(revisionForm, source ? {
    projectType: source.projectType, signingMode: source.signingMode, implementationMode: source.implementationMode,
    businessPurposeCode: source.businessPurposeCode, applicableTimingCode: source.applicableTimingCode,
    priority: source.priority, threshold: String(source.threshold), ruleVersion: source.ruleVersion
  } : { projectType: '*', signingMode: '*', implementationMode: '*', businessPurposeCode: 'ACCEPTANCE',
    applicableTimingCode: 'MANUAL', priority: 100, threshold: '80.00', ruleVersion: 'SAT-RULE-V1' })
  selected.value = row
  mode.value = 'revision'
  dialogVisible.value = true
}
const save = async () => {
  if (saving.value) return
  if (mode.value === 'template' && (!templateForm.templateCode.trim() || !templateForm.name.trim())) {
    message.warning('请填写模板编码和名称'); return
  }
  if (mode.value === 'revision' && designError.value) { message.warning(designError.value); return }
  saving.value = true
  try {
    if (mode.value === 'template') {
      const created = await Api.createTemplate(templateForm)
      await load()
      openRevision(created)
      message.success('模板已创建，请配置问卷题目')
      return
    }
    else if (selected.value)
      await Api.createRevision(selected.value.id, {
        ...revisionForm,
        questionnaireJson: previewJson.value,
        threshold: Number(revisionForm.threshold)
      })
    message.success('保存成功')
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const publish = async (row: TemplateView) => {
  const revision = draftRevision(row) as TemplateRevision
  const conflict = templates.value.find(other => other.id !== row.id && other.status === 'PUBLISHED' && (() => {
    const current = currentRevision(other)
    return current && ['projectType', 'signingMode', 'implementationMode', 'businessPurposeCode', 'applicableTimingCode', 'priority']
      .every(key => current[key as keyof TemplateRevision] === revision[key as keyof TemplateRevision])
  })())
  if (conflict) {
    message.warning(`与“${conflict.name}”的适用条件和优先级相同，请新建修订调整配置后发布`)
    return
  }
  await message.confirm(`确认发布修订 V${revision.revisionNo}？发布后配置不可修改。`)
  await Api.publishRevision(row.id, revision)
  message.success('发布成功')
  await load()
}
onMounted(load)
</script>

<style scoped lang="scss">
.dimension-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}
@media (width <= 767px) {
  .dimension-grid {
    grid-template-columns: 1fr;
  }
}
</style>
