<template>
  <div class="panel-heading">
    <div
      ><h2>满意度采集任务</h2
      ><p>指派责任人、创建一次性受控链接，或由当前责任人现场协助提交。</p></div
    >
    <div class="toolbar"
      ><el-input-number
        v-model="projectId"
        :min="1"
        controls-position="right"
        placeholder="项目ID"
      /><el-button :loading="loading" @click="load">查询</el-button></div
    >
  </div>
  <el-skeleton v-if="loading" :rows="4" animated />
  <el-empty v-else-if="!tasks.length" description="当前范围内暂无满意度任务" />
  <el-table v-else :data="tasks" stripe>
    <el-table-column prop="id" label="任务ID" min-width="150" />
    <el-table-column prop="projectId" label="项目ID" min-width="150" />
    <el-table-column prop="revisionNo" label="轮次" width="80" />
    <el-table-column prop="assignedToUserId" label="责任人" min-width="130" />
    <el-table-column prop="status" label="任务状态" min-width="140" />
    <el-table-column prop="questionnaireStatus" label="问卷状态" min-width="120" />
    <el-table-column label="操作" width="340" fixed="right">
      <template #default="scope">
        <el-button link type="primary" @click="openAssign(scope.row)">指派</el-button>
        <el-button link type="primary" @click="openGrant(scope.row)">受控链接</el-button>
        <el-button link type="primary" @click="openAssisted(scope.row)">现场协助</el-button>
        <el-button v-if="scope.row.resultId" link type="warning" @click="openRecollect(scope.row)"
          >整改重收</el-button
        >
      </template>
    </el-table-column>
  </el-table>

  <el-dialog v-model="assignVisible" title="指派采集责任人" width="460px">
    <el-form label-position="top"
      ><el-form-item label="用户ID"
        ><el-input-number v-model="assignedUserId" :min="1" /></el-form-item
    ></el-form>
    <template #footer
      ><el-button @click="assignVisible = false">取消</el-button
      ><el-button type="primary" @click="assign">确认指派</el-button></template
    >
  </el-dialog>

  <el-dialog v-model="grantVisible" title="受控问卷链接" width="min(560px, 94vw)" destroy-on-close>
    <template v-if="!grantUrl">
      <el-form label-position="top"
        ><el-form-item label="有效期"
          ><el-date-picker
            v-model="grantExpiresAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
      ></el-form>
      <el-alert
        title="链接只在本次创建后显示，请立即交付给客户。"
        type="warning"
        :closable="false"
        show-icon
      />
    </template>
    <div v-else class="grant-result">
      <Qrcode :text="grantUrl" :width="200" />
      <el-input :model-value="grantUrl" readonly
        ><template #append><el-button @click="copyLink">复制</el-button></template></el-input
      >
    </div>
    <template #footer
      ><el-button @click="closeGrant">关闭</el-button
      ><el-button v-if="!grantUrl" type="primary" @click="createGrant"
        >创建链接</el-button
      ></template
    >
  </el-dialog>

  <el-dialog v-model="assistedVisible" title="现场协助提交" width="min(720px, 94vw)">
    <el-alert
      title="签字及附件必须是当前用户有权读取的PLT公共文件事实；服务端会再次重验并绑定到答卷。"
      type="info"
      :closable="false"
    />
    <el-form label-position="top" class="dialog-form">
      <el-form-item label="客户联系人"
        ><el-input v-model="assisted.customerContactRef"
      /></el-form-item>
      <el-form-item label="答卷 JSON"
        ><el-input v-model="assisted.answerSnapshot" type="textarea" :rows="7" spellcheck="false"
      /></el-form-item>
      <el-form-item label="文件事实 JSON（至少一条 SIGNATURE）"
        ><el-input v-model="assisted.filesJson" type="textarea" :rows="8" spellcheck="false"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="assistedVisible = false">取消</el-button
      ><el-button type="primary" @click="submitAssisted">提交并判定</el-button></template
    >
  </el-dialog>

  <el-dialog v-model="recollectVisible" title="登记整改并重收" width="min(620px, 94vw)">
    <el-form label-position="top">
      <el-form-item label="整改证据摘要"
        ><el-input v-model="recollectForm.evidenceSummary" type="textarea" :rows="4"
      /></el-form-item>
      <el-form-item label="证据文件事实版本（可选）"
        ><el-input v-model="recollectForm.evidenceFileFactVersion"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="recollectVisible = false">取消</el-button
      ><el-button type="primary" @click="submitRecollect">创建下一轮</el-button></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { Qrcode } from '@/components/Qrcode'
import { getTenantId } from '@/utils/auth'
import * as Api from '@/api/pms/project/satisfaction'
import type { TaskView } from '@/api/pms/project/satisfaction'

const message = useMessage()
const loading = ref(false)
const projectId = ref<number>()
const tasks = ref<TaskView[]>([])
const selected = ref<TaskView>()
const assignVisible = ref(false)
const assignedUserId = ref<number>()
const grantVisible = ref(false)
const grantExpiresAt = ref('')
const grantUrl = ref('')
const assistedVisible = ref(false)
const assisted = reactive({
  customerContactRef: '',
  answerSnapshot: '{\n  "answers": []\n}',
  filesJson: '[\n  {\n    "role": "SIGNATURE",\n    "sequence": 1\n  }\n]'
})
const recollectVisible = ref(false)
const recollectForm = reactive({ evidenceSummary: '', evidenceFileFactVersion: '' })

const load = async () => {
  loading.value = true
  try {
    tasks.value = await Api.listTasks(projectId.value)
  } finally {
    loading.value = false
  }
}
const openAssign = (task: TaskView) => {
  selected.value = task
  assignedUserId.value = task.assignedToUserId
  assignVisible.value = true
}
const assign = async () => {
  if (!selected.value || !assignedUserId.value) return
  await Api.assignTask(selected.value, assignedUserId.value)
  message.success('指派成功')
  assignVisible.value = false
  await load()
}
const openGrant = (task: TaskView) => {
  selected.value = task
  grantUrl.value = ''
  grantExpiresAt.value = new Date(Date.now() + 24 * 3600_000).toISOString().slice(0, 19)
  grantVisible.value = true
}
const createGrant = async () => {
  if (!selected.value || !grantExpiresAt.value) return
  const grant = await Api.createGrant(selected.value.id, grantExpiresAt.value)
  const tenantId = getTenantId()
  grantUrl.value = `${window.location.origin}/satisfaction-questionnaires/${encodeURIComponent(grant.token)}?tenantId=${tenantId}`
}
const copyLink = async () => {
  await navigator.clipboard.writeText(grantUrl.value)
  message.success('链接已复制')
}
const closeGrant = () => {
  grantVisible.value = false
  grantUrl.value = ''
}
const openAssisted = (task: TaskView) => {
  selected.value = task
  assistedVisible.value = true
}
const submitAssisted = async () => {
  if (!selected.value) return
  const files = JSON.parse(assisted.filesJson)
  await Api.submitAssisted(selected.value.id, {
    requestId: crypto.randomUUID(),
    customerContactRef: assisted.customerContactRef,
    answerSnapshot: assisted.answerSnapshot,
    files
  })
  message.success('现场协助答卷已提交并完成判定')
  assistedVisible.value = false
  await load()
}
const openRecollect = (task: TaskView) => {
  selected.value = task
  recollectVisible.value = true
}
const submitRecollect = async () => {
  if (!selected.value?.resultId) return
  await Api.recollect(selected.value.id, {
    priorResultId: selected.value.resultId,
    remediationRequestId: crypto.randomUUID(),
    evidenceSummary: recollectForm.evidenceSummary,
    evidenceFileFactVersion: recollectForm.evidenceFileFactVersion || undefined
  })
  message.success('整改事实与下一轮问卷已创建')
  recollectVisible.value = false
  await load()
}
onMounted(load)
</script>

<style scoped lang="scss">
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.panel-heading h2 {
  margin: 0;
  font-size: 18px;
}
.panel-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
}
.toolbar {
  display: flex;
  gap: 8px;
}
.grant-result {
  display: grid;
  justify-items: center;
  gap: 18px;
}
.dialog-form {
  margin-top: 16px;
}
@media (width <= 767px) {
  .panel-heading {
    flex-direction: column;
  }
  .toolbar {
    width: 100%;
  }
  .toolbar > * {
    flex: 1;
  }
}
</style>
