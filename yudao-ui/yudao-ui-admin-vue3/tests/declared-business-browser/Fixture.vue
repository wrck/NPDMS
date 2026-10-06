<template>
  <main style="max-width: 980px; margin: 24px auto">
    <button data-testid="login" @click="login">登录隔离测试账号</button>
    <button data-testid="create" :disabled="!detail || executing" @click="create">创建并保留丢响应意图</button>
    <button data-testid="recover" :disabled="!pendingIntent || executing" @click="recover">查询原回执并绑定表单</button>
    <button data-testid="save" :disabled="!current || executing" @click="save">保存公共表单</button>
    <button data-testid="readonly" :disabled="!current" @click="readonly = true">打开公共只读视图</button>
    <p data-testid="status">{{ status }}</p>
    <p data-testid="pending">{{ pendingIntent ? 'pending' : 'none' }}</p>
    <section v-if="current && !readonly" data-testid="editable-form">
      <BusinessEntityForm ref="form" :writable-fields="writableFields" :fields="detail?.fields"
        :initial-values="current.fieldValues" :presentation="formPresentation" :disabled="executing" />
    </section>
    <section v-if="current && readonly" data-testid="runtime-view">
      <DeclaredBusinessView owner-module="IT" entity-type="declaredNote" :entity-id="current.ref.entityId"
        :stable-code="detail!.stableCode" :readonly="true" :allowed-actions="[]" />
    </section>
  </main>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import BusinessEntityForm from '@/components/BusinessEntity/BusinessEntityForm.vue'
import DeclaredBusinessView from '@/components/BusinessView/DeclaredBusinessView.vue'
import { useBusinessEntity } from '@/components/BusinessEntity/useBusinessEntity'
import request from './request'
const entity = useBusinessEntity(() => 'IT', () => 'declaredNote')
const { detail, writableFields, current, formPresentation, pendingIntent, executing } = entity
const status = ref(''), readonly = ref(false), form = ref<{ buildInput: () => Promise<Record<string, unknown>> }>()
const run = async (action: () => Promise<void>) => { try { await action() } catch (error) { status.value = (error as Error).message } }
const login = () => run(async () => {
  const credentials = await fetch('/fixture/credentials').then(response => response.json())
  await request.post({ url: '/admin-api/system/auth/login', data: credentials })
  sessionStorage.setItem('it-authenticated', 'true')
  await entity.loadDetail(); status.value = 'authenticated'
})
const create = () => run(async () => {
  const receipt = await entity.execute(detail.value!.operations.find(operation => operation.code === 'create')!, undefined,
    { projectRef: 99, title: 'private initial content', internalMemo: 'DO_NOT_PERSIST' })
  await entity.readEntity(receipt.entityRef.entityId); status.value = 'created'
})
const recover = () => run(async () => {
  const receipt = await entity.recover()
  if (!receipt) throw new Error('receipt missing')
  const data = await entity.readEntity(receipt.entityRef.entityId)
  if (!formPresentation.value?.layout) {
    await request.post({ url: '/api/v1/pms/business-models/IT/declaredNote/form/binding', params: { entityId: data.ref.entityId },
      data: { expectedEntityVersion: data.concurrencyBasis, expectedBindingVersion: 0, formRevisionId: 900101,
        fieldBindings: { heading: 'title' }, bindRemainingFields: true } })
    await entity.readEntity(data.ref.entityId)
  }
  status.value = 'recovered'
})
const save = () => run(async () => {
  const receipt = await entity.execute(detail.value!.operations.find(operation => operation.code === 'save')!,
    current.value!.ref.entityId, await form.value!.buildInput(), current.value!.concurrencyBasis)
  await entity.readEntity(receipt.entityRef.entityId); status.value = 'saved'
})
onMounted(async () => { if (sessionStorage.getItem('it-authenticated')) await entity.loadDetail() })
Object.assign(window, { declaredFixture: { entity, request } })
</script>
