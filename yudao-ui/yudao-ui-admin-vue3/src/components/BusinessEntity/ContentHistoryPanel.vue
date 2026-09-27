<template>
  <el-card shadow="never" class="mt-8px">
    <template #header>
      <div class="flex items-center justify-between">
        <span>内容历史（继承式修订）</span>
        <el-button link type="primary" :loading="loading" @click="reload">刷新</el-button>
      </div>
    </template>

    <el-alert v-if="panelError" :title="panelError" type="error" :closable="false" show-icon class="mb-8px" />

    <!-- 发起修订：从当前生效内容（或指定来源修订）复制草稿 -->
    <el-form inline class="mb-8px">
      <el-form-item label="修订原因">
        <el-input v-model="createReason" placeholder="如 修正现场信息" style="width: 220px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="creating" :disabled="!createReason || hasOpenDraft" @click="createDraft">
          发起修订
        </el-button>
      </el-form-item>
    </el-form>

    <h4>修订历史</h4>
    <el-table
      :data="revisions"
      row-key="ref.revisionId"
      size="small"
      border
      highlight-current-row
      @current-change="selectRevision"
    >
      <el-table-column prop="revisionNo" label="修订号" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.state === 'DRAFT' ? 'warning' : 'info'" size="small">
            {{ row.state === 'DRAFT' ? '草稿' : '已冻结' }}
          </el-tag>
          <el-tag v-if="row.effective" type="success" size="small" class="ml-4px">当前生效</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
      <el-table-column prop="version" label="修订版本" width="90" />
      <el-table-column prop="baseEntityVersion" label="创建基线版本" width="110" />
      <el-table-column prop="frozenAt" label="冻结时间" min-width="150" show-overflow-tooltip />
    </el-table>

    <!-- 修订内容编辑：字段目录驱动，仅声明可写字段可改 -->
    <template v-if="selected">
      <h4 class="mt-8px">
        修订 #{{ selected.revisionNo }} 内容
        <el-tag v-if="selected.state === 'FROZEN'" type="info" size="small">已冻结，只读</el-tag>
      </h4>
      <el-form label-width="120px" class="mb-8px">
        <el-form-item v-for="field in writableFields" :key="field.code" :label="field.name">
          <el-switch v-if="field.type === 'BOOLEAN'" v-model="editForm[field.code]" />
          <el-input-number v-else-if="field.type === 'NUMBER'" v-model="editForm[field.code]" />
          <el-date-picker
            v-else-if="field.type === 'DATE'"
            v-model="editForm[field.code]"
            type="date"
            value-format="YYYY-MM-DD"
          />
          <el-date-picker
            v-else-if="field.type === 'DATETIME'"
            v-model="editForm[field.code]"
            type="datetime"
            value-format="YYYY-MM-DD'T'HH:mm:ss"
          />
          <el-input v-else v-model="editForm[field.code]" />
        </el-form-item>
        <el-form-item v-if="selected.state === 'DRAFT'">
          <el-button type="primary" :loading="saving" @click="saveDraft">保存修订</el-button>
          <el-button type="success" :loading="completing" @click="complete">冻结并生效</el-button>
          <el-button type="danger" plain :loading="discarding" @click="discard">放弃草稿</el-button>
        </el-form-item>
        <el-form-item v-else>
          <el-button type="success" :loading="completing" :disabled="selected.effective" @click="complete">
            生效此修订
          </el-button>
        </el-form-item>
      </el-form>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { BusinessEntityData, FieldVO } from '@/api/pms/platform/businessmodel'
import { getEntityData } from '@/api/pms/platform/businessmodel'
import type { RevisionVO } from '@/api/pms/platform/entityversion'
import { completeRevision, createRevision, discardRevision, listRevisions, saveRevision } from '@/api/pms/platform/entityversion'
import { serverErrorMessage } from './useBusinessEntity'

defineOptions({ name: 'ContentHistoryPanel' })
const props = defineProps<{
  ownerModule: string
  entityType: string
  entityId: number
  fields?: FieldVO[]
  current?: BusinessEntityData
}>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const loading = ref(false)
const creating = ref(false)
const saving = ref(false)
const completing = ref(false)
const discarding = ref(false)
const panelError = ref('')
const revisions = ref<RevisionVO[]>([])
const selected = ref<RevisionVO>()
const createReason = ref('')
const editForm = ref<Record<string, unknown>>({})

const writableFields = computed(() => (props.fields ?? []).filter((field) => field.writable))
const hasOpenDraft = computed(() => revisions.value.some((revision) => revision.state === 'DRAFT'))

const load = async () => {
  loading.value = true
  try {
    revisions.value =
      (await listRevisions(props.ownerModule, props.entityType, props.entityId, { limit: 50 })) ?? []
  } catch (error: any) {
    panelError.value = serverErrorMessage(error, '修订历史加载失败')
  } finally {
    loading.value = false
  }
}

const createDraft = async () => {
  creating.value = true
  panelError.value = ''
  try {
    await createRevision(props.ownerModule, props.entityType, props.entityId, {
      reason: createReason.value
    })
    createReason.value = ''
    await load()
    emit('changed')
  } catch (error: any) {
    panelError.value = serverErrorMessage(error, '发起修订失败')
  } finally {
    creating.value = false
  }
}

const selectRevision = async (row: RevisionVO | null) => {
  if (!row) return
  selected.value = row
  try {
    const data = await getEntityData(props.ownerModule, props.entityType, {
      id: props.entityId,
      revisionId: row.ref.revisionId
    })
    const values: Record<string, unknown> = {}
    for (const field of writableFields.value) {
      values[field.code] = data?.fieldValues?.[field.code] ?? null
    }
    editForm.value = values
  } catch (error: any) {
    panelError.value = serverErrorMessage(error, '修订内容读取失败')
  }
}

const saveDraft = async () => {
  if (!selected.value) return
  saving.value = true
  panelError.value = ''
  try {
    const revision = await saveRevision(props.ownerModule, props.entityType, props.entityId,
      selected.value.ref.revisionId, { expectedVersion: selected.value.version, fields: editForm.value })
    await load()
    await selectUpdated(revision?.version)
    emit('changed')
  } catch (error: any) {
    panelError.value = serverErrorMessage(error, '修订保存失败')
  } finally {
    saving.value = false
  }
}

const complete = async () => {
  if (!selected.value) return
  completing.value = true
  panelError.value = ''
  try {
    await completeRevision(props.ownerModule, props.entityType, props.entityId,
      selected.value.ref.revisionId, { expectedVersion: selected.value.version })
    selected.value = undefined
    await load()
    emit('changed')
  } catch (error: any) {
    panelError.value = serverErrorMessage(error, '修订生效失败')
  } finally {
    completing.value = false
  }
}

// 放弃草稿只删除未冻结的草稿工作区；已冻结修订是不可变历史，不提供放弃。
const discard = async () => {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm('放弃后草稿及其未生效内容将删除，确定放弃该修订草稿？', '放弃草稿', {
      type: 'warning',
      confirmButtonText: '放弃',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  discarding.value = true
  panelError.value = ''
  try {
    await discardRevision(props.ownerModule, props.entityType, props.entityId,
      selected.value.ref.revisionId)
    selected.value = undefined
    await load()
  } catch (error: any) {
    panelError.value = serverErrorMessage(error, '草稿放弃失败')
  } finally {
    discarding.value = false
  }
}

// 保存后按修订号找回该行并刷新编辑表单的预期版本，避免旧版本号导致下一次保存并发冲突。
const selectUpdated = async (version?: number) => {
  if (!selected.value) return
  const row = revisions.value.find(
    (item) => item.ref.revisionId === selected.value!.ref.revisionId
  )
  if (row) {
    if (version != null) row.version = version
    selected.value = row
  }
}

const reload = () => load()

onMounted(load)
</script>
