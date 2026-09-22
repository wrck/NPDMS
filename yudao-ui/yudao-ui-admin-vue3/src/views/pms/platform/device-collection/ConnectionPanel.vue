<template>
  <div class="flex gap-12px mb-16px">
    <el-button type="primary" v-hasPermi="['pms:device-credential:create']" @click="openSave()"
      >验证并保存连接</el-button
    >
    <el-button :loading="loading" @click="load">刷新</el-button>
  </div>
  <el-alert
    title="密码由 DAC 加密保存，页面不回显。连接仅在指定项目、设备、协议和有效期内授权使用。"
    type="info"
    :closable="false"
    class="mb-16px"
  />
  <el-table v-loading="loading" :data="rows" empty-text="当前项目下暂无本人保存的连接">
    <el-table-column prop="name" label="连接名称" min-width="160" />
    <el-table-column prop="deviceId" label="设备编号" width="110" />
    <el-table-column label="连接目标" min-width="190"
      ><template #default="{ row }"
        >{{ row.protocol }} {{ row.host }}:{{ row.port }}</template
      ></el-table-column
    >
    <el-table-column prop="username" label="用户名" min-width="120" />
    <el-table-column label="状态" width="140"
      ><template #default="{ row }"
        ><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{
          row.status === 'ACTIVE' ? '已保存' : row.status === 'PENDING' ? '保存待完成' : '已停用'
        }}</el-tag></template
      ></el-table-column
    >
    <el-table-column label="操作" width="230"
      ><template #default="{ row }">
        <el-button
          v-if="row.status === 'PENDING'"
          link
          type="primary"
          v-hasPermi="['pms:device-credential:create']"
          @click="openSave(row)"
          >继续保存</el-button
        >
        <el-button
          v-if="row.status === 'ACTIVE'"
          link
          type="primary"
          v-hasPermi="['pms:device-credential:grant']"
          @click="openGrants(row)"
          >使用授权</el-button
        >
        <el-button
          v-if="row.status !== 'DISABLED'"
          link
          type="warning"
          :disabled="busy"
          v-hasPermi="['pms:device-credential:update']"
          @click="disable(row)"
          >停用</el-button
        >
      </template></el-table-column
    >
  </el-table>
  <Dialog
    v-model="saveVisible"
    title="验证并保存设备连接"
    width="min(700px, 95vw)"
    @closed="clearSecret"
  >
    <el-alert
      v-if="frozen"
      title="已保留原保存请求，请重新输入密码继续确认。设备和授权范围已固定；如需更换请停用此记录后新建连接。"
      type="warning"
      :closable="false"
      class="mb-16px"
    />
    <el-form ref="saveRef" :model="form" :rules="saveRules" label-width="100px" autocomplete="off">
      <el-form-item label="连接名称" prop="name"
        ><el-input
          v-model="form.name"
          maxlength="64"
          :disabled="busy || frozen"
          placeholder="租户内唯一的名称"
      /></el-form-item>
      <el-form-item label="目标设备" prop="deviceId"
        ><ProjectDeviceSelect
          v-model="form.deviceId"
          :project-id="projectId"
          :disabled="busy || frozen"
      /></el-form-item>
      <el-form-item label="协议" prop="protocol"
        ><el-radio-group
          v-model="form.protocol"
          :disabled="busy || frozen"
          @change="form.port = form.protocol === 'SSH' ? 22 : 23"
          ><el-radio value="SSH">SSH</el-radio
          ><el-radio value="TELNET">Telnet</el-radio></el-radio-group
        ></el-form-item
      >
      <el-row :gutter="16">
        <el-col :xs="24" :sm="16"
          ><el-form-item label="设备地址" prop="host"
            ><el-input v-model="form.host" :disabled="busy || frozen" /></el-form-item
        ></el-col>
        <el-col :xs="24" :sm="8"
          ><el-form-item label="端口" prop="port"
            ><el-input-number
              v-model="form.port"
              :min="1"
              :max="65535"
              controls-position="right"
              :disabled="busy || frozen"
              class="!w-full" /></el-form-item
        ></el-col>
      </el-row>
      <el-form-item label="用户名" prop="username"
        ><el-input v-model="form.username" :disabled="busy || frozen" autocomplete="off"
      /></el-form-item>
      <el-form-item label="密码" prop="secret"
        ><el-input
          v-model="form.secret"
          type="password"
          :disabled="busy"
          autocomplete="new-password"
      /></el-form-item>
      <el-form-item v-if="frozen && form.templateId" label="原模板限定">
        <span>{{ form.templateId }}（保留原保存请求的授权范围）</span>
      </el-form-item>
      <el-form-item label="授权到期" prop="expiresAt"
        ><el-date-picker
          v-model="form.expiresAt"
          type="datetime"
          value-format="x"
          :disabled="busy || frozen"
          class="!w-full"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button type="primary" :loading="busy" @click="save">验证并保存</el-button
      ><el-button :disabled="busy" @click="saveVisible = false">关闭</el-button></template
    >
  </Dialog>
  <Dialog
    v-model="grantVisible"
    :title="`连接使用授权 · ${selected?.name || ''}`"
    width="min(900px, 95vw)"
  >
    <el-form
      ref="grantRef"
      :model="grantForm"
      :rules="grantRules"
      label-width="90px"
      :disabled="busy"
    >
      <el-form-item label="使用人" prop="userId"
        ><el-select v-model="grantForm.userId" filterable class="!w-full"
          ><el-option v-for="u in users" :key="u.id" :value="u.id" :label="u.nickname" /></el-select
      ></el-form-item>
      <el-form-item label="到期时间" prop="expiresAt"
        ><el-date-picker
          v-model="grantForm.expiresAt"
          type="datetime"
          value-format="x"
          class="!w-full"
      /></el-form-item>
      <el-form-item
        ><el-button type="primary" :loading="busy" @click="grant"
          >新增使用授权</el-button
        ></el-form-item
      >
    </el-form>
    <el-table :data="grantRows">
      <el-table-column label="使用人" min-width="110"
        ><template #default="{ row }">{{
          users.find((u) => String(u.id) === row.granteeId)?.nickname || row.granteeId
        }}</template></el-table-column
      >
      <el-table-column label="模板" min-width="190"
        ><template #default="{ row }">{{
          row.templateId === '*'
            ? '不限模板（含手工命令）'
            : `限定模板 ${row.templateId || '未指定'}`
        }}</template></el-table-column
      >
      <el-table-column
        prop="expiresAt"
        label="到期时间"
        min-width="170"
        :formatter="dateFormatter"
      />
      <el-table-column label="状态" width="100"
        ><template #default="{ row }">{{
          row.status === 'ACTIVE' ? '有效' : row.status === 'EXPIRED' ? '已过期' : '已撤销'
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="90"
        ><template #default="{ row }"
          ><el-button
            v-if="row.status === 'ACTIVE'"
            link
            type="warning"
            :disabled="busy"
            @click="revoke(row)"
            >撤销</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <p class="text-[var(--el-text-color-secondary)]"
      >授权仅适用于此连接绑定的设备和协议；使用人仍需具备对应项目与业务操作权限。撤销或停用后，后台会请求取消仍在执行的任务。</p
    >
    <template #footer><el-button @click="grantVisible = false">关闭</el-button></template>
  </Dialog>
</template>
<script setup lang="ts">
import * as Api from '@/api/pms/platform/deviceCollection'
import * as UserApi from '@/api/system/user'
import ProjectDeviceSelect from '@/components/ProjectDeviceSelect/index.vue'
import { dateFormatter } from '@/utils/formatTime'
import { generateUUID } from '@/utils'
const props = defineProps<{ projectId: Api.Id }>()
const message = useMessage()
const loading = ref(false)
const busy = ref(false)
const rows = ref<Api.Connection[]>([])
const saveVisible = ref(false)
const saveRef = ref()
const frozen = ref(false)
const form = reactive({
  requestKey: '',
  name: '',
  deviceId: undefined as Api.Id | undefined,
  host: '',
  port: 22,
  protocol: 'SSH',
  username: '',
  secret: '',
  templateId: undefined as Api.Id | undefined,
  expiresAt: ''
})
const saveRules = Object.fromEntries(
  ['name', 'deviceId', 'host', 'port', 'protocol', 'username', 'secret', 'expiresAt'].map((key) => [
    key,
    [{ required: true, message: '请填写此项', trigger: 'change' }]
  ])
)
const load = async () => {
  loading.value = true
  try {
    rows.value = await Api.connections(props.projectId)
  } finally {
    loading.value = false
  }
}
const clearSecret = () => {
  form.secret = ''
}
const openSave = async (row?: Api.Connection) => {
  clearSecret()
  Object.assign(
    form,
    row
      ? {
          requestKey: row.registrationKey,
          name: row.name,
          deviceId: row.deviceId,
          host: row.host,
          port: row.port,
          protocol: row.protocol,
          username: row.username,
          secret: '',
          templateId: row.registrationTemplateId,
          expiresAt: String(row.registrationExpiresAt || '')
        }
      : {
          requestKey: generateUUID(),
          name: '',
          deviceId: undefined,
          host: '',
          port: 22,
          protocol: 'SSH',
          username: '',
          secret: '',
          templateId: undefined,
          expiresAt: ''
        }
  )
  frozen.value = !!row
  saveVisible.value = true
}
const save = async () => {
  await saveRef.value.validate()
  busy.value = true
  try {
    await Api.saveConnection({
      ...form,
      projectId: props.projectId,
      deviceId: form.deviceId!,
      templateId: form.templateId
    })
    saveVisible.value = false
    message.success('连接已验证并加密保存，已建立本人使用授权')
  } catch {
    // Never let a request object containing a secret reach Vue's unhandled error logger.
    frozen.value = true
    message.warning('保存未完成，请查看提示；刷新连接列表后可继续原请求')
  } finally {
    clearSecret()
    busy.value = false
    await load()
    if (saveVisible.value) {
      frozen.value = rows.value.some((row) => row.registrationKey === form.requestKey)
    }
  }
}
const disable = async (row: Api.Connection) => {
  await message.confirm('确认停用此连接？将禁止新的下发，执行中的任务会收到取消请求。')
  busy.value = true
  try {
    await Api.disableConnection(row.id)
    await load()
  } finally {
    busy.value = false
  }
}
const selected = ref<Api.Connection>()
const grantVisible = ref(false)
const grantRef = ref()
const grantRows = ref<Api.Grant[]>([])
const users = ref<UserApi.UserVO[]>([])
const grantForm = reactive({
  userId: undefined as Api.Id | undefined,
  templateId: undefined as Api.Id | undefined,
  expiresAt: ''
})
const grantRules = Object.fromEntries(
  ['userId', 'expiresAt'].map((key) => [
    key,
    [{ required: true, message: '请填写此项', trigger: 'change' }]
  ])
)
const openGrants = async (row: Api.Connection) => {
  selected.value = row
  const result = await Promise.all([Api.grants(row.id), UserApi.getSimpleUserList()])
  grantRows.value = result[0]
  users.value = result[1]
  Object.assign(grantForm, { userId: undefined, templateId: undefined, expiresAt: '' })
  grantVisible.value = true
}
const grant = async () => {
  await grantRef.value.validate()
  busy.value = true
  try {
    await Api.grantConnection(selected.value!.id, {
      userId: grantForm.userId!,
      expiresAt: grantForm.expiresAt
    })
    grantRows.value = await Api.grants(selected.value!.id)
    message.success('使用授权已保存')
  } finally {
    busy.value = false
  }
}
const revoke = async (row: Api.Grant) => {
  await message.confirm('确认撤销此使用授权？')
  busy.value = true
  try {
    await Api.revokeGrant(selected.value!.id, row.id)
    grantRows.value = await Api.grants(selected.value!.id)
  } finally {
    busy.value = false
  }
}
onMounted(load)
onBeforeUnmount(clearSecret)
</script>
