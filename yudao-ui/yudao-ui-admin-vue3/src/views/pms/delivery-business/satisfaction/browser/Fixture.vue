<template>
  <main>
    <aside>
      <p>满意度既有组件验证：业务 API 为内存夹具，不连接数据库或生产系统。</p>
      <el-button @click="projectId = 1">切换至项目1（慢响应）</el-button>
      <el-button @click="projectId = 2">切换至项目2</el-button>
      <el-button @click="projectId = 0">无效项目</el-button>
      <el-button @click="openStandalone">独立管理入口</el-button>
      <el-switch v-model="readonly" active-text="只读" />
      <span data-testid="dirty">{{ dirty ? '有未结束操作' : '无未结束操作' }}</span>
    </aside>
    <Workbench :project-id="projectId" :readonly="readonly" @dirty-change="dirty = $event" />
    <details
      ><summary>测试调用记录</summary><pre data-testid="calls">{{ JSON.stringify(calls) }}</pre>
    </details>
  </main>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import Workbench from '@/views/pms/project/satisfaction/index.vue'
import { calls } from './fixtureApi'
const projectId = ref<number | undefined>(2)
const readonly = ref(false)
const dirty = ref(false)
const openStandalone = () => {
  projectId.value = undefined
  readonly.value = false
}
</script>
<style>
body {
  margin: 0;
  font-family: sans-serif;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
}
main {
  max-width: 1100px;
  margin: auto;
  padding: 20px;
  background: var(--el-bg-color);
}
aside {
  margin-bottom: 20px;
}
aside .el-button {
  margin: 4px;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 767px) {
  main {
    padding: 12px;
  }
}
</style>
