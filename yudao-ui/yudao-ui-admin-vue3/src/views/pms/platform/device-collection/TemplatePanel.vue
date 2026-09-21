<template>
  <div class="flex gap-12px mb-16px">
    <el-button type="primary" v-hasPermi="['pms:collection-template:create']" @click="edit()"
      >新建命令模板</el-button
    >
    <el-button :loading="loading" @click="load">刷新</el-button>
  </div>
  <el-table v-loading="loading" :data="rows" empty-text="暂无命令模板">
    <el-table-column prop="name" label="模板名称" min-width="160" />
    <el-table-column prop="code" label="标识" min-width="160" />
    <el-table-column label="用途" min-width="140"
      ><template #default="{ row }">{{ Api.purposes[row.purpose] }}</template></el-table-column
    >
    <el-table-column prop="protocol" label="协议" width="90" />
    <el-table-column label="适用型号" min-width="120"
      ><template #default="{ row }">{{ row.deviceModel || '不限型号' }}</template></el-table-column
    >
    <el-table-column prop="revision" label="版本" width="80" />
    <el-table-column label="状态" min-width="140"
      ><template #default="{ row }"
        ><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{
          state(row)
        }}</el-tag></template
      ></el-table-column
    >
    <el-table-column label="操作" width="290"
      ><template #default="{ row }">
        <el-button link type="primary" @click="inspect(row)">查看命令</el-button>
        <el-button
          v-if="row.status === 'DRAFT' && !row.publicationStarted"
          link
          type="primary"
          v-hasPermi="['pms:collection-template:update']"
          @click="edit(row)"
          >编辑</el-button
        >
        <el-button
          link
          type="primary"
          v-hasPermi="['pms:collection-template:create']"
          @click="edit(row, true)"
          >新建版本</el-button
        >
        <el-button
          v-if="row.status === 'DRAFT'"
          link
          type="success"
          :disabled="busy"
          v-hasPermi="['pms:collection-template:publish']"
          @click="publish(row)"
          >{{ row.publicationStarted ? '重试发布' : '发布' }}</el-button
        >
        <el-button
          v-if="row.status === 'PUBLISHED'"
          link
          type="warning"
          :disabled="busy"
          v-hasPermi="['pms:collection-template:publish']"
          @click="retire(row)"
          >停用</el-button
        >
      </template></el-table-column
    >
  </el-table>
  <Dialog
    v-model="visible"
    :title="readOnly ? '查看命令模板' : form.id ? '编辑模板草稿' : '新建模板版本'"
    width="min(760px, 95vw)"
  >
    <el-alert
      title="发布后命令内容固定；后续修改请新建版本。停用仅阻止新执行，历史任务保留原始命令。"
      type="info"
      :closable="false"
      class="mb-16px"
    />
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      :disabled="readOnly || busy"
    >
      <el-form-item label="模板标识" prop="code"
        ><el-input
          v-model="form.code"
          :disabled="!!form.id || newVersion"
          maxlength="64"
          placeholder="字母、数字和连字符"
      /></el-form-item>
      <el-form-item label="名称" prop="name"
        ><el-input v-model="form.name" maxlength="128"
      /></el-form-item>
      <el-form-item label="版本号" prop="revision"
        ><el-input-number v-model="form.revision" :min="1" :disabled="!!form.id"
      /></el-form-item>
      <el-form-item label="用途" prop="purpose"
        ><el-select v-model="form.purpose" class="!w-full"
          ><el-option
            v-for="(label, value) in Api.purposes"
            :key="value"
            :label="label"
            :value="value" /></el-select
      ></el-form-item>
      <el-form-item label="连接协议" prop="protocol"
        ><el-radio-group v-model="form.protocol"
          ><el-radio value="SSH">SSH</el-radio
          ><el-radio value="TELNET">Telnet</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item label="适用型号"
        ><el-input
          v-model="form.deviceModel"
          maxlength="128"
          placeholder="可选；填写后只允许型号完全相符的设备"
      /></el-form-item>
      <el-form-item label="执行命令" prop="commands"
        ><el-input
          v-model="form.commands"
          type="textarea"
          :rows="9"
          maxlength="65536"
          placeholder="每行一条命令；请勿填写密码、密钥等凭据"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button v-if="!readOnly" type="primary" :loading="busy" @click="save">保存草稿</el-button
      ><el-button @click="visible = false">关闭</el-button></template
    >
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/pms/platform/deviceCollection'
const message = useMessage()
const rows = ref<Api.Template[]>([])
const loading = ref(false)
const busy = ref(false)
const visible = ref(false)
const readOnly = ref(false)
const newVersion = ref(false)
const formRef = ref()
const empty = (): Api.Template => ({
  code: '',
  name: '',
  purpose: 'configuration',
  protocol: 'SSH',
  revision: 1,
  commands: ''
})
const form = ref<Api.Template>(empty())
const rules = Object.fromEntries(
  ['code', 'name', 'revision', 'purpose', 'protocol', 'commands'].map((key) => [
    key,
    [{ required: true, message: '请填写此项', trigger: 'blur' }]
  ])
)
const state = (row: Api.Template) =>
  row.status === 'PUBLISHED'
    ? '已发布'
    : row.status === 'RETIRED'
      ? '已停用'
      : row.publicationStarted
        ? '发布待确认（已冻结）'
        : '草稿'
const load = async () => {
  loading.value = true
  try {
    rows.value = await Api.templates()
  } finally {
    loading.value = false
  }
}
const edit = (row?: Api.Template, next = false) => {
  readOnly.value = false
  newVersion.value = next
  form.value = row ? { ...row } : empty()
  if (next && row)
    form.value = {
      ...row,
      id: undefined,
      version: undefined,
      status: 'DRAFT',
      publicationStarted: false,
      revision:
        Math.max(...rows.value.filter((r) => r.code === row.code).map((r) => r.revision)) + 1
    }
  visible.value = true
}
const inspect = (row: Api.Template) => {
  edit(row)
  readOnly.value = true
}
const save = async () => {
  await formRef.value.validate()
  busy.value = true
  try {
    await Api.saveTemplate(form.value)
    visible.value = false
    message.success('草稿已保存')
    await load()
  } finally {
    busy.value = false
  }
}
const publish = async (row: Api.Template) => {
  await message.confirm(`确认发布「${row.name}」v${row.revision}？命令内容将冻结。`)
  busy.value = true
  try {
    await Api.publishTemplate(row)
    message.success('模板已发布')
  } finally {
    busy.value = false
    await load()
  }
}
const retire = async (row: Api.Template) => {
  await message.confirm('确认停用此版本？新的采集任务将不能使用此版本。')
  busy.value = true
  try {
    await Api.retireTemplate(row)
    await load()
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
