import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/config/axios', () => ({ default: api }))
import { createProjectMemberFilterSource } from '@/api/pms/project/projects'
import Scoped from '@/components/SystemUserSelect/SystemUserScoped.vue'
import { mount, passthrough, textOf } from '@/views/pms/platform/dynamic-form/components/runtimeTestHarness'
const apps: any[] = []
const flush = async () => { for(let i=0;i<10;i++) await nextTick() }
const render = (source: ReturnType<typeof createProjectMemberFilterSource>, props = {}) => {
 const update=vi.fn();const page=mount(Scoped,{scopedSource:source,'onUpdate:modelValue':update,...props},{ElSelect:passthrough,ElOption:passthrough,ElTag:passthrough});apps.push(page.app);return {...page,state:(page.vm as any).$.setupState,update}
}
beforeEach(()=>api.get.mockReset())
afterEach(()=>apps.splice(0).forEach(app=>app.unmount()))
it.each(['MANAGER','SERVICE_MANAGER','SALES'] as const)('keeps the published UserSelect source server-scoped for %s',async field=>{
 api.get.mockResolvedValue({list:[{id:'2115387679367467008',username:'h06523',nickname:'成员',deptName:'项目部门'}],total:21})
 const page=render(createProjectMemberFilterSource(field));expect(api.get).not.toHaveBeenCalled();page.state.open(true);await flush();expect(textOf(page.root)).toContain('项目部门');page.state.search('h06523');await flush();page.state.more();await flush()
 expect(api.get).toHaveBeenLastCalledWith({url:'/api/v1/pms/projects/member-filter-options',params:{field,keyword:'h06523',pageNo:2,pageSize:20}})
 expect(api.get.mock.calls.every(([query])=>query.url==='/api/v1/pms/projects/member-filter-options')).toBe(true)
})
it('resolves a selected large ID only from that source cached authorized pages',async()=>{
 const source=createProjectMemberFilterSource('MANAGER');expect(await source.resolve!(['2115387679367467008'])).toEqual([]);expect(api.get).not.toHaveBeenCalled()
 const row={id:'2115387679367467008',username:'h06523',nickname:'已选成员'};api.get.mockResolvedValueOnce({list:[row],total:1});await source.search({keyword:'',pageNo:1,pageSize:20})
 const page=render(source,{modelValue:row.id});await flush();expect(page.state.labelFor(row.id)).toBe('已选成员');expect(page.state.historicalIds).toEqual([row.id]);expect(page.update).not.toHaveBeenCalled()
 api.get.mockResolvedValueOnce({list:[],total:0});page.state.search('none');await flush();expect(page.state.labelFor(row.id)).toBe('已选成员');expect(page.state.rows).toEqual([])
 expect(await createProjectMemberFilterSource('SALES').resolve!([row.id])).toEqual([])
})
it('failed scoped API calls do not fall back to any directory or drop the selected ID',async()=>{
 api.get.mockRejectedValueOnce(new Error('403'));const page=render(createProjectMemberFilterSource('SALES'),{modelValue:'2115387679367467008'});page.state.open(true);await flush();expect(page.state.error).toContain('失败');expect(page.update).not.toHaveBeenCalled();expect(api.get).toHaveBeenCalledTimes(1)
 api.get.mockResolvedValueOnce({list:[],total:0});page.state.retry();await flush();expect(api.get.mock.calls.every(([query])=>query.url==='/api/v1/pms/projects/member-filter-options')).toBe(true)
})
