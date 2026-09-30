<script setup lang="ts">
import '@/styles/management.css'
import { onMounted } from 'vue'
import AppShell from '@/components/AppShell.vue'
import RequestState from '@/components/management/RequestState.vue'
import { managementApi } from '@/api/management'
import { createRequest } from '@/management/use-request'
import type { ManagementSettings } from '@/types/management'
const { data, loading, error, run } = createRequest<ManagementSettings>()
const labels = { scheduleEnabled: '到期通知调度', callbackEnabled: '回调', masterDataEnabled: '主数据集成', telnetEnabled: 'Telnet', serialEnabled: '串口', credentialStorageAvailable: '凭据存储可用' }
const load = () => run(managementApi.settings)
onMounted(load)
</script>
<template>
  <AppShell
    class="management-page"
    element-layout
    title="平台设置"
    subtitle="只读身份与能力视图"
  >
    <el-card shadow="never">
      <template #header>
        <el-space>
          <h2>当前身份与部署能力</h2><el-button
            :loading="loading"
            @click="load"
          >
            刷新
          </el-button>
        </el-space>
      </template><RequestState
        :loading="loading"
        :error="error"
        @retry="load"
      >
        <template v-if="data">
          <el-alert
            title="能力开关不代表实际授权"
            type="info"
            :closable="false"
          /><el-descriptions
            :column="1"
            border
          >
            <el-descriptions-item label="平台">
              {{ data.platformName }} / {{ data.apiVersion }}
            </el-descriptions-item><el-descriptions-item label="认证模式">
              {{ data.authMode }} <el-tag
                v-if="data.localDebug"
                type="warning"
              >
                本地调试身份
              </el-tag>
            </el-descriptions-item><el-descriptions-item label="当前身份">
              {{ data.subject || '未提供' }}
            </el-descriptions-item><el-descriptions-item label="命名空间 范围">
              {{ data.allNamespaces ? '全部命名空间（显式授权）' : data.namespaces?.join(', ') || '无' }}
            </el-descriptions-item><el-descriptions-item label="项目范围">
              {{ data.projects?.join(', ') || '无' }}
            </el-descriptions-item><el-descriptions-item label="权限范围">
              <el-space wrap>
                <el-tag
                  v-for="scope in data.scopes"
                  :key="scope"
                  type="info"
                >
                  {{ scope }}
                </el-tag>
              </el-space>
            </el-descriptions-item><el-descriptions-item label="解析输入上限">
              {{ data.maxParserInputBytes }} 字节（UTF-8）
            </el-descriptions-item><el-descriptions-item
              v-for="(label, key) in labels"
              :key="key"
              :label="label"
            >
              <el-tag :type="data.capabilities[key] ? 'success' : 'info'">
                {{ data.capabilities[key] ? '已启用' : '未启用' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </RequestState>
    </el-card>
  </AppShell>
</template>
