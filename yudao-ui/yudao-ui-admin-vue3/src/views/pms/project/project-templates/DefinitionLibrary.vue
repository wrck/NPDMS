<template>
  <ContentWrap>
    <el-alert title="可复用定义库 · 发布版正文不可改，复制产生同编码下一修订；停用仅阻止新引用，不影响历史解释。配置不赋予业务权限。" type="info" :closable="false" class="mb-16px" />
    <el-form inline>
      <el-form-item label="定义类型"><el-select v-model="query.definitionKind" clearable class="!w-170px"><el-option v-for="(label, kind) in Api.definitionKinds" :key="kind" :value="kind" :label="label" /></el-select></el-form-item>
      <el-form-item label="精确编码"><el-input v-model="query.definitionCode" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="修订状态"><el-select v-model="query.revisionState" clearable class="!w-140px"><el-option value="DRAFT" label="草稿" /><el-option value="PUBLISHED" label="已发布（含停用）" /></el-select></el-form-item>
      <el-form-item><el-button @click="search">查询定义</el-button><el-button type="primary" @click="create" v-hasPermi="['pms:project-template:update']">新增定义</el-button></el-form-item>
    </el-form>
    <el-alert v-if="listFailure" :title="listFailure" type="error" :closable="false" role="alert" />
    <el-table :data="rows" v-loading="loading" empty-text="无定义；可先创建并发布基础规则、权限与绑定，再组装阶段/任务">
      <el-table-column label="类型" width="130"><template #default="{ row }">{{ Api.definitionKinds[row.definitionKind] }}</template></el-table-column>
      <el-table-column prop="definitionCode" label="稳定编码" min-width="180" />
      <el-table-column prop="revisionNo" label="修订" width="80" />
      <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.disabledAt ? 'warning' : row.revisionState === 'PUBLISHED' ? 'success' : 'info'">{{ row.disabledAt ? '已停用' : row.revisionState === 'PUBLISHED' ? '已发布' : '草稿' }}</el-tag></template></el-table-column>
      <el-table-column prop="publishedAt" label="发布时间" min-width="170" />
      <el-table-column prop="version" label="并发版本" width="100" />
      <el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" @click="open(row.id)" v-hasPermi="['pms:project-template:query']">查看 / 管理</el-button></template></el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </ContentWrap>
  <el-drawer v-model="visible" :title="selected ? `${selected.definitionCode} · r${selected.revisionNo}` : '新增可复用定义'" size="min(960px, 96vw)" :before-close="beforeClose">
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" role="alert" class="mb-12px" />
    <el-alert v-if="stale" title="当前修订或依赖已变化。未自动覆盖编辑内容；请重新读取权威版本，再明确重试。" type="warning" :closable="false" class="mb-12px" />
    <el-form label-width="150px" :disabled="readonly || busy">
      <el-form-item label="类型"><el-select :model-value="form.definitionKind" :disabled="!!selected" @update:model-value="changeKind"><el-option v-for="(label, kind) in Api.definitionKinds" :key="kind" :value="kind" :label="label" /></el-select></el-form-item>
      <el-form-item label="稳定编码"><el-input v-model="form.definitionCode" :disabled="!!selected" placeholder="创建后不可修改；复制保留身份" /></el-form-item>
      <el-form-item label="Schema版本"><el-input :model-value="form.schemaVersion" disabled /></el-form-item>
    </el-form>
    <DefinitionForm :key="form.definitionKind" ref="formRef" :model="form" :disabled="readonly || busy" />
    <el-alert v-if="validation" :type="validation.valid ? 'success' : 'error'" :title="validation.valid ? '服务端预检通过；发布仍会重验依赖' : '服务端预检未通过'" :closable="false" class="mt-12px">
      <ul><li v-for="(issue, index) in validation.issues" :key="index">{{ issue.field }} · {{ issue.code }} · {{ issue.message }}</li></ul>
    </el-alert>
    <template #footer>
      <el-button v-if="selected" :disabled="busy" @click="refresh">重新读取</el-button>
      <el-button v-if="!readonly" type="primary" :loading="busy" :disabled="stale" @click="save" v-hasPermi="['pms:project-template:update']">保存定义草稿</el-button>
      <el-button v-if="selected" :disabled="busy" @click="validate" v-hasPermi="['pms:project-template:query']">校验定义</el-button>
      <el-button v-if="selected && !readonly" type="success" :disabled="busy || stale" @click="command('publish')" v-hasPermi="['pms:project-template:publish']">发布定义</el-button>
      <el-button v-if="selected" :disabled="busy" @click="command('copy')" v-hasPermi="['pms:project-template:update']">复制下一修订</el-button>
      <el-button v-if="selected?.revisionState === 'PUBLISHED' && !selected.disabledAt" type="warning" :disabled="busy || stale" @click="command('disable')" v-hasPermi="['pms:project-template:disable']">停用定义</el-button>
    </template>
  </el-drawer>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import * as Api from '@/api/pms/project/project-templates/definitions'
import { isBusinessViewConflict } from '@/api/pms/platform/business-view'
import DefinitionForm from './DefinitionForm.vue'
import { commandIntent, errorText } from './editorModel'
const message = useMessage()
const emit = defineEmits<{ changed: [] }>()
const query = ref<Api.DefinitionQuery>({ pageNo: 1, pageSize: 10 })
const rows = ref<Api.DefinitionRevision[]>([])
const total = ref(0)
const loading = ref(false)
const listFailure = ref('')
const visible = ref(false)
const selected = ref<Api.DefinitionRevision>()
const busy = ref(false)
const failure = ref('')
const stale = ref(false)
const validation = ref<Api.ValidationResult>()
const formRef = ref<InstanceType<typeof DefinitionForm>>()
const initial = (kind: Api.DefinitionKind): Api.DefinitionSave => {
  const rule = () => ({ predicate: '', parameters: { refCode: '' } })
  const payloads: Record<Api.DefinitionKind, Api.JsonObject> = {
    STAGE: { name: '', stageCode: '', start: false, terminal: false, workBinding: 'workBinding', permissionPolicy: 'permissionPolicy', completionRule: 'completionRule' },
    TASK: { name: '', workBinding: 'workBinding', permissionPolicy: 'permissionPolicy', completionRule: 'completionRule' },
    WORK_BINDING: { bindingType: '', instanceResolutionStrategy: '', contextMapping: {} },
    COMPLETION_RULE: rule(), PERMISSION_POLICY: { requiredActions: [] },
    DELIVERABLE: { scope: 'STAGE', deliverableType: '', required: true, minimumQuantity: 1, allowedSources: [], outputType: '', confirmationRule: rule() },
    GATE: { gateType: 'ENTRY', references: [] }, MILESTONE: { name: '', criteria: '' }
  }
  return { definitionKind: kind, definitionCode: '', schemaVersion: 1, payload: payloads[kind], references: [] }
}
const form = ref<Api.DefinitionSave>(initial('STAGE'))
const baseline = ref('')
const dirty = computed(() => JSON.stringify(form.value) !== baseline.value)
const readonly = computed(() => !!selected.value && (selected.value.revisionState !== 'DRAFT' || !!selected.value.disabledAt))
const intent = commandIntent()
const load = async () => {
  loading.value = true
  listFailure.value = ''
  try {
    const page = await Api.getDefinitionPage({ ...query.value, definitionCode: query.value.definitionCode || undefined, definitionKind: query.value.definitionKind || undefined, revisionState: query.value.revisionState || undefined })
    rows.value = page.list
    total.value = page.total
  } catch (error) { listFailure.value = errorText(error) }
  finally { loading.value = false }
}
const search = () => { query.value.pageNo = 1; load() }
const resetResult = () => { failure.value = ''; stale.value = false; validation.value = undefined; intent.clear() }
const create = () => {
  selected.value = undefined
  form.value = initial(query.value.definitionKind || 'STAGE')
  baseline.value = JSON.stringify(form.value)
  resetResult()
  visible.value = true
}
const changeKind = (kind: Api.DefinitionKind) => {
  const code = form.value.definitionCode
  form.value = initial(kind)
  form.value.definitionCode = code
  validation.value = undefined
}
const open = async (id: number) => {
  try {
    const row = await Api.getDefinition(id)
    selected.value = row
    form.value = JSON.parse(JSON.stringify({ definitionKind: row.definitionKind, definitionCode: row.definitionCode, schemaVersion: row.schemaVersion, payload: row.payload, references: row.references }))
    baseline.value = JSON.stringify(form.value)
    resetResult()
    visible.value = true
  } catch (error) { failure.value = errorText(error); listFailure.value = failure.value }
}
const beforeClose = async (done: () => void) => {
  if (busy.value) return
  if (dirty.value) { try { await message.confirm('有未保存定义，确认放弃本地编辑？') } catch { return } }
  done()
}
const refresh = async () => {
  if (!selected.value) return
  if (dirty.value) { try { await message.confirm('重新读取将替换未保存编辑，是否继续？') } catch { return } }
  await open(selected.value.id)
}
const failed = (error: any) => { failure.value = errorText(error); stale.value = isBusinessViewConflict(error); validation.value = undefined }
const save = async () => {
  if (readonly.value || busy.value || stale.value) return
  if (!form.value.definitionCode.trim()) { failure.value = '定义编码不能为空。'; return }
  if (!formRef.value?.validate()) return
  busy.value = true
  failure.value = ''
  try {
    const data = JSON.parse(JSON.stringify(form.value))
    const key = intent.key({ action: 'save', id: selected.value?.id, version: selected.value?.version, data })
    let id = selected.value?.id
    if (selected.value) await Api.updateDefinition(selected.value.id, selected.value.version, data, key)
    else id = await Api.createDefinition(data, key)
    intent.clear()
    await open(id!)
    await load()
    emit('changed')
  } catch (error) { failed(error) }
  finally { busy.value = false }
}
const validate = async () => {
  if (!selected.value || busy.value) return
  if (dirty.value) { failure.value = '请先保存当前修改；预检只读取服务端已保存草稿。'; return }
  busy.value = true
  failure.value = ''
  try { validation.value = await Api.validateDefinition(selected.value.id) }
  catch (error) { failed(error) }
  finally { busy.value = false }
}
const command = async (action: 'copy' | 'publish' | 'disable') => {
  if (!selected.value || busy.value) return
  if (dirty.value) { failure.value = '请先保存或重新读取；操作仅针对当前已保存修订。'; return }
  try { await message.confirm({ copy: '复制为同编码下一草稿修订？旧版本不会修改。', publish: '发布此定义？发布前将重新校验实际依赖。', disable: '停用此定义？仅阻止新引用，历史保留。' }[action]) } catch { return }
  busy.value = true
  failure.value = ''
  try {
    if (action === 'publish') {
      validation.value = await Api.validateDefinition(selected.value.id)
      if (!validation.value.valid) return
    }
    const row = selected.value
    const key = intent.key({ action, id: row.id, version: row.version })
    const result = await ({ copy: Api.copyDefinition, publish: Api.publishDefinition, disable: Api.disableDefinition }[action])(row.id, row.version, key)
    intent.clear()
    await open(action === 'copy' ? result : row.id)
    await load()
    emit('changed')
  } catch (error) { failed(error) }
  finally { busy.value = false }
}
onMounted(load)
</script>
