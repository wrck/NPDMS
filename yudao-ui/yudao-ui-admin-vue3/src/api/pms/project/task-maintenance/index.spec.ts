import { expect,it,vi } from 'vitest'
import request from '@/config/axios'
import { createNativeTask, getMembers } from './index'
vi.mock('@/config/axios',()=>({ default:{ post:vi.fn(), get:vi.fn() } }))
it('keeps the creation metadata separate from rich text and preserves the caller intent',async()=>{
  await createNativeTask(8,{taskCode:'T1',name:'任务',stageCode:'S1',parentTaskId:'2098269178197524481',description:'<p>正文</p>'},'same-key')
  expect(request.post).toHaveBeenCalledWith({url:'/api/v1/pms/projects/8/tasks/native',data:{task:{taskCode:'T1',name:'任务',stageCode:'S1',parentTaskId:'2098269178197524481'},descriptionHtml:'<p>正文</p>'},headers:{'Idempotency-Key':'same-key'}})
})
it('coalesces concurrent role candidate requests without caching later membership reads',async()=>{
  let finish!: (value: { list: never[]; total: number })=>void
  vi.mocked(request.get).mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve}))
  const first=getMembers(8,{pageNo:1,pageSize:20}), second=getMembers(8,{pageNo:1,pageSize:20})
  expect(request.get).toHaveBeenCalledTimes(1)
  finish({list:[],total:0}); await Promise.all([first,second])
  vi.mocked(request.get).mockResolvedValueOnce({list:[],total:0})
  await getMembers(8,{pageNo:1,pageSize:20})
  expect(request.get).toHaveBeenCalledTimes(2)
})
