<template>
  <main>
    <aside>
      <p>S1项目工期原组件验证：业务API、文件与BPM为测试夹具，无生产或数据库访问。</p>
      <el-button @click="project.id = 1">项目1</el-button>
      <el-button @click="project.id = 2">项目2</el-button>
      <el-button @click="project.id = 0">无效项目</el-button>
      <el-switch v-model="readonly" active-text="只读" />
      <span data-testid="dirty">{{ dirty ? '有未结束操作' : '无未结束操作' }}</span>
    </aside>
    <Panel :project="project" :readonly="readonly" @dirty-change="dirty = $event" />
    <details
      ><summary>测试调用记录</summary><pre data-testid="calls">{{ JSON.stringify(calls) }}</pre>
    </details>
  </main>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import Panel from '@/views/pms/project/project-master-detail/components/ProjectDurationPanel.vue'
import { calls } from './fixtureApi'
const project = reactive({ id: 1, version: 7 })
const readonly = ref(false)
const dirty = ref(false)
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
