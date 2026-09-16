<!-- src/components/ConnectorDesigner/StepAuth.vue -->
<script setup lang="ts">
/**
 * 连接器分步表单 — Step 2 认证配置（REST）/ 数据源配置（DB）。
 *
 * <p>REST：authType 下拉（NONE/BASIC/BEARER/API_KEY），按类型展示对应字段 + baseUrl。
 * DB：driverClassName / url / username / password / maxPoolSize。</p>
 */
import { reactive, watch } from 'vue'
import type { AuthType, ConnectorType } from '@/api/lowcode-connector'

const props = defineProps<{
  type: ConnectorType
  authType: AuthType
  username: string
  password: string
  token: string
  headerName: string
  apiKey: string
  baseUrl: string
  driverClassName: string
  dbUrl: string
  dbUsername: string
  dbPassword: string
  maxPoolSize: number
}>()

const emit = defineEmits<{
  'update:authType': [value: AuthType]
  'update:username': [value: string]
  'update:password': [value: string]
  'update:token': [value: string]
  'update:headerName': [value: string]
  'update:apiKey': [value: string]
  'update:baseUrl': [value: string]
  'update:driverClassName': [value: string]
  'update:dbUrl': [value: string]
  'update:dbUsername': [value: string]
  'update:dbPassword': [value: string]
  'update:maxPoolSize': [value: number]
}>()

const form = reactive({
  authType: props.authType,
  username: props.username,
  password: props.password,
  token: props.token,
  headerName: props.headerName,
  apiKey: props.apiKey,
  baseUrl: props.baseUrl,
  driverClassName: props.driverClassName,
  dbUrl: props.dbUrl,
  dbUsername: props.dbUsername,
  dbPassword: props.dbPassword,
  maxPoolSize: props.maxPoolSize
})

watch(
  () => [
    props.authType,
    props.username,
    props.password,
    props.token,
    props.headerName,
    props.apiKey,
    props.baseUrl,
    props.driverClassName,
    props.dbUrl,
    props.dbUsername,
    props.dbPassword,
    props.maxPoolSize
  ],
  ([
    authType,
    username,
    password,
    token,
    headerName,
    apiKey,
    baseUrl,
    driverClassName,
    dbUrl,
    dbUsername,
    dbPassword,
    maxPoolSize
  ]) => {
    form.authType = authType as AuthType
    form.username = username as string
    form.password = password as string
    form.token = token as string
    form.headerName = headerName as string
    form.apiKey = apiKey as string
    form.baseUrl = baseUrl as string
    form.driverClassName = driverClassName as string
    form.dbUrl = dbUrl as string
    form.dbUsername = dbUsername as string
    form.dbPassword = dbPassword as string
    form.maxPoolSize = maxPoolSize as number
  }
)

watch(form, (val) => {
  emit('update:authType', val.authType)
  emit('update:username', val.username)
  emit('update:password', val.password)
  emit('update:token', val.token)
  emit('update:headerName', val.headerName)
  emit('update:apiKey', val.apiKey)
  emit('update:baseUrl', val.baseUrl)
  emit('update:driverClassName', val.driverClassName)
  emit('update:dbUrl', val.dbUrl)
  emit('update:dbUsername', val.dbUsername)
  emit('update:dbPassword', val.dbPassword)
  emit('update:maxPoolSize', val.maxPoolSize)
})

const DRIVER_PRESETS = [
  { label: 'MySQL 8', value: 'com.mysql.cj.jdbc.Driver' },
  { label: 'MySQL 5.x', value: 'com.mysql.jdbc.Driver' },
  { label: 'PostgreSQL', value: 'org.postgresql.Driver' },
  { label: 'SQL Server', value: 'com.microsoft.sqlserver.jdbc.SQLServerDriver' },
  { label: 'Oracle', value: 'oracle.jdbc.OracleDriver' }
]
</script>

<template>
  <div class="step-auth">
    <!-- REST 认证配置 -->
    <el-form v-if="props.type === 'REST'" :model="form" label-width="120px">
      <el-form-item label="认证类型">
        <el-select v-model="form.authType" style="width: 240px">
          <el-option label="无认证 (NONE)" value="NONE" />
          <el-option label="Basic 认证" value="BASIC" />
          <el-option label="Bearer Token" value="BEARER" />
          <el-option label="API Key" value="API_KEY" />
        </el-select>
      </el-form-item>

      <template v-if="form.authType === 'BASIC'">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="basic auth 用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="basic auth 密码" />
        </el-form-item>
      </template>

      <template v-else-if="form.authType === 'BEARER'">
        <el-form-item label="Token">
          <el-input
            v-model="form.token"
            type="password"
            show-password
            placeholder="Bearer token 值"
          />
        </el-form-item>
      </template>

      <template v-else-if="form.authType === 'API_KEY'">
        <el-form-item label="Header 名称">
          <el-input v-model="form.headerName" placeholder="如 X-API-Key" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="API Key 值" />
        </el-form-item>
      </template>

      <el-divider content-position="left">服务地址</el-divider>
      <el-form-item label="Base URL" required>
        <el-input v-model="form.baseUrl" placeholder="https://api.example.com" />
      </el-form-item>
    </el-form>

    <!-- DB 数据源配置 -->
    <el-form v-else :model="form" label-width="120px">
      <el-form-item label="驱动类名" required>
        <el-select
          v-model="form.driverClassName"
          filterable
          allow-create
          default-first-option
          style="width: 360px"
        >
          <el-option
            v-for="d in DRIVER_PRESETS"
            :key="d.value"
            :label="`${d.label} — ${d.value}`"
            :value="d.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="JDBC URL" required>
        <el-input
          v-model="form.dbUrl"
          placeholder="jdbc:mysql://localhost:3306/dppms_d365?useSSL=false"
        />
      </el-form-item>
      <el-form-item label="用户名" required>
        <el-input v-model="form.dbUsername" />
      </el-form-item>
      <el-form-item label="密码" required>
        <el-input v-model="form.dbPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="连接池大小">
        <el-input-number v-model="form.maxPoolSize" :min="1" :max="100" />
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.step-auth {
  max-width: 640px;
}
</style>
