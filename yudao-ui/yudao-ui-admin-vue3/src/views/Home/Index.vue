<template>
  <main class="delivery-home">
    <header class="welcome">
      <div><p class="welcome-kicker">项目交付工作台</p><h1>{{ username }}，你好</h1><p>从待办和项目开始，继续推进交付工作。</p></div>
      <button v-if="canTodo" class="welcome-action" type="button" @click="router.push('/bpm/task/todo')">查看我的待办 <Icon icon="ep:right" /></button>
    </header>
    <div class="workspace">
      <section v-if="canTodo" class="panel tasks" aria-labelledby="tasks-title">
        <div class="panel-heading"><div><h2 id="tasks-title">我的待办</h2><p>需要你办理的流程任务</p></div><el-button link type="primary" @click="router.push('/bpm/task/todo')">查看全部</el-button></div>
        <el-skeleton v-if="todoLoading" :rows="4" animated />
        <p v-else-if="todoError" class="empty">待办加载失败，请稍后重试。 <el-button link type="primary" @click="loadTodos">重试</el-button></p>
        <p v-else-if="!todos.length" class="empty">暂无待办任务</p>
        <ul v-else class="rows"><li v-for="task in todos" :key="task.id"><div><strong>{{ task.processInstance?.name || task.name || '待办任务' }}</strong><span>{{ task.name || '流程任务' }}</span></div><span>{{ task.processInstance?.startUser?.nickname || '—' }}</span></li></ul>
      </section>
      <section v-if="canProject" class="panel projects" aria-labelledby="projects-title">
        <div class="panel-heading"><div><h2 id="projects-title">项目列表</h2><p>查看当前账号可访问的项目</p></div><el-button link type="primary" @click="router.push('/pms/project-management/projects')">查看全部</el-button></div>
        <el-skeleton v-if="projectLoading" :rows="4" animated />
        <p v-else-if="projectError" class="empty">项目加载失败，请稍后重试。 <el-button link type="primary" @click="loadProjects">重试</el-button></p>
        <p v-else-if="!projects.length" class="empty">暂无可访问的项目</p>
        <ul v-else class="rows"><li v-for="project in projects" :key="project.id"><button class="project-link" type="button" @click="openProject(project.id)"><strong>{{ project.projectName || '未命名项目' }}</strong><span>{{ project.projectCode || '—' }}</span></button><span>{{ project.customerName || '—' }}</span></li></ul>
      </section>
      <aside class="panel shortcuts"><div class="panel-heading"><div><h2>常用功能</h2><p>进入已有业务工作台</p></div></div><nav aria-label="常用功能"><button v-for="item in visibleShortcuts" :key="item.path" type="button" @click="router.push(item.path)"><Icon :icon="item.icon" :size="20" /><span><strong>{{ item.name }}</strong><small>{{ item.description }}</small></span><Icon icon="ep:arrow-right" /></button><p v-if="!visibleShortcuts.length" class="empty">暂无可访问的快捷入口</p></nav></aside>
    </div>
  </main>
</template>
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { checkPermi } from '@/utils/permission'
import { getTaskTodoPage } from '@/api/bpm/task'
import { getProjectPage, type ProjectMasterVO } from '@/api/pms/project/projects'

defineOptions({ name: 'Index' })
const router = useRouter()
const userStore = useUserStore()
const username = computed(() => userStore.getUser.nickname || '用户')
const canTodo = computed(() => checkPermi(['bpm:task:query']))
const canProject = computed(() => checkPermi(['pms:project:query']))
const shortcuts = [
  { name: '项目列表', description: '查看项目及阶段任务', path: '/pms/project-management/projects', permission: 'pms:project:query', icon: 'ep:folder-opened' },
  { name: '我的待办', description: '办理流程任务', path: '/bpm/task/todo', permission: 'bpm:task:query', icon: 'ep:document-checked' },
  { name: '割接任务', description: '查看和办理割接', path: '/pms/cutover/tasks', permission: 'pms:cutover-task:query', icon: 'ep:connection' },
  { name: '巡检任务', description: '查看巡检任务', path: '/pms/inspection/tasks', permission: 'pms:inspection-task:query', icon: 'ep:search' }
]
const visibleShortcuts = computed(() => shortcuts.filter(item => checkPermi([item.permission]) && router.getRoutes().some(route => route.path === item.path)))
const todos = ref<Array<{ id: string; name?: string; processInstance?: { name?: string; startUser?: { nickname?: string } } }>>([])
const projects = ref<ProjectMasterVO[]>([])
const todoLoading = ref(false), projectLoading = ref(false), todoError = ref(false), projectError = ref(false)
const loadTodos = async () => { todoLoading.value = true; todoError.value = false; try { const data = await getTaskTodoPage({ pageNo: 1, pageSize: 5 }); todos.value = data?.list || [] } catch { todoError.value = true } finally { todoLoading.value = false } }
const loadProjects = async () => { projectLoading.value = true; projectError.value = false; try { const data = await getProjectPage({ pageNo: 1, pageSize: 5 }); projects.value = data?.list || [] } catch { projectError.value = true } finally { projectLoading.value = false } }
const openProject = (id?: number) => { if (id) router.push({ path: '/pms/project-management/project-master-detail', query: { projectId: id } }) }
onMounted(() => { if (canTodo.value) void loadTodos(); if (canProject.value) void loadProjects() })
</script>
<style scoped>
.delivery-home{--ink:#17324d;--muted:var(--el-text-color-secondary);--line:var(--el-border-color-lighter);--accent:var(--el-color-primary);max-width:1520px;margin:auto;color:var(--ink)}
.welcome{display:flex;justify-content:space-between;align-items:end;gap:24px;padding:28px 32px;background:#e9f3fb;border-left:5px solid var(--accent);border-radius:5px;color:#17324d}.welcome-kicker{color:#356a94;font-size:13px;font-weight:600}.welcome h1{font-size:27px;line-height:1.3;margin:7px 0}.welcome p:last-child{color:#526b80}.panel-heading p{color:var(--muted);margin:5px 0 0;font-size:13px}.welcome-action{border:0;border-radius:4px;background:var(--accent);color:white;padding:11px 16px;cursor:pointer;white-space:nowrap}
.workspace{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr) 280px;gap:16px;margin-top:16px;align-items:start}.panel{background:var(--el-bg-color-overlay,#fff);border:1px solid var(--line);border-radius:5px;padding:22px;min-width:0}.panel-heading{display:flex;justify-content:space-between;gap:12px;align-items:start;margin-bottom:20px}.panel h2{font-size:17px;margin:0;color:var(--el-text-color-primary)}.rows{list-style:none;margin:0;padding:0}.rows li{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:15px 0;border-top:1px solid var(--line);font-size:13px;color:var(--muted)}.rows strong,.rows span{display:block}.rows strong{font-size:14px;color:var(--el-text-color-primary);font-weight:600}.rows div span,.project-link span{margin-top:6px}.project-link{border:0;background:none;padding:0;text-align:left;cursor:pointer}.project-link:hover strong{color:var(--accent)}.shortcuts nav{display:grid}.shortcuts nav button{display:flex;align-items:center;gap:12px;padding:14px 0;text-align:left;border:0;border-top:1px solid var(--line);background:none;color:var(--accent);cursor:pointer}.shortcuts nav button>span{flex:1}.shortcuts strong,.shortcuts small{display:block}.shortcuts strong{font-size:14px;color:var(--el-text-color-primary)}.shortcuts small{margin-top:4px;color:var(--muted)}.empty{color:var(--muted);font-size:14px;padding:24px 0}button:focus-visible{outline:2px solid var(--accent);outline-offset:3px}@media(max-width:1200px){.workspace{grid-template-columns:repeat(2,minmax(0,1fr))}.shortcuts{grid-column:1/-1}.shortcuts nav{grid-template-columns:repeat(2,minmax(0,1fr));column-gap:24px}}@media(max-width:700px){.welcome{align-items:start;flex-direction:column;padding:22px}.workspace{grid-template-columns:1fr}.shortcuts{grid-column:auto}.shortcuts nav{grid-template-columns:1fr}}
</style>
