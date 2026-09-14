<template>
  <div>
    <ContentWrap>
      <div class="integration-toolbar">
        <el-button v-hasPermi="['pms:integration:connection']" type="primary" plain @click="open()"
          ><Icon icon="ep:plus" class="mr-5px" />新增连接</el-button
        >
        <el-button :loading="loading" @click="load"
          ><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button
        >
      </div>
    </ContentWrap>
    <ContentWrap>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-4" />
      <el-table
        v-loading="loading"
        :data="rows"
        class="integration-table"
        empty-text="暂无连接，可新建连接或引用已有数据源"
      >
        <el-table-column prop="name" label="连接名称" min-width="160" />
        <el-table-column prop="url" label="MySQL 地址" min-width="240" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column label="操作" width="180"
          ><template #default="{ row }">
            <el-button link type="primary" @click="open(row)">编辑</el-button>
            <el-button link type="primary" :loading="testing === row.id" @click="test(row)"
              >测试连接</el-button
            >
          </template></el-table-column
        >
      </el-table>
    </ContentWrap>
    <el-dialog
      v-model="visible"
      :title="draft.id ? '编辑连接' : '新增连接'"
      class="integration-editor"
      width="min(640px, calc(100vw - 32px))"
      destroy-on-close
    >
      <el-form
        ref="form"
        :model="draft"
        label-width="120px"
        class="integration-form"
        @submit.prevent="save"
      >
        <el-form-item v-if="!draft.id" label="引用已有数据源">
          <el-select
            v-model="draft.dataSourceId"
            clearable
            placeholder="不选择则新建"
            @visible-change="loadSources"
          >
            <el-option v-for="s in available" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item
          label="连接名称"
          prop="name"
          :rules="[{ required: true, message: '请输入名称' }]"
          ><el-input v-model="draft.name"
        /></el-form-item>
        <template v-if="!draft.dataSourceId">
          <el-form-item
            label="JDBC URL"
            prop="url"
            :rules="[{ required: true, message: '请输入 MySQL URL' }]"
          >
            <el-input v-model="draft.url" placeholder="jdbc:mysql://host:3306/database" />
          </el-form-item>
          <el-form-item
            label="用户名"
            prop="username"
            :rules="[{ required: true, message: '请输入用户名' }]"
            ><el-input v-model="draft.username" autocomplete="off"
          /></el-form-item>
          <el-form-item label="密码">
            <el-input v-model="draft.password" type="password" autocomplete="new-password" />
            <p class="integration-note">加密保存，不会回显。编辑时留空表示不变。</p>
          </el-form-item>
        </template>
      </el-form>
      <template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存连接</el-button></template
      >
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import * as api from '@/api/pms/integration'
import type { FormInstance } from 'element-plus'
const message = useMessage()
const rows = ref<api.Connection[]>([]),
  available = ref<api.Connection[]>([])
const loading = ref(false),
  saving = ref(false),
  visible = ref(false),
  error = ref('')
const testing = ref<api.Id>(),
  form = ref<FormInstance>()
const draft = ref<{
  id?: api.Id
  dataSourceId?: api.Id
  name: string
  url: string
  username: string
  password: string
}>({ name: '', url: '', username: '', password: '' })
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    rows.value = (await api.getConnections()).list
  } catch {
    error.value = '连接列表加载失败，请重试。'
  } finally {
    loading.value = false
  }
}
const open = (row?: api.Connection) => {
  draft.value = { name: '', url: '', username: '', ...row, password: '' }
  visible.value = true
}
const loadSources = async (show: boolean) => {
  if (show) available.value = await api.getDataSources()
}
const save = async () => {
  if (!(await form.value?.validate())) return
  saving.value = true
  try {
    await api.saveConnection(draft.value)
    draft.value.password = ''
    visible.value = false
    await load()
    message.success('连接已保存')
  } finally {
    saving.value = false
  }
}
const test = async (row: api.Connection) => {
  testing.value = row.id
  try {
    await api.testConnection(row.id)
    message.success('连接正常')
  } finally {
    testing.value = undefined
  }
}
onMounted(load)
</script>
