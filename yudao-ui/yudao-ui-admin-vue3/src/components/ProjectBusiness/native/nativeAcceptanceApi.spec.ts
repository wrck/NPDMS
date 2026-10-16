import { expect, it, vi } from 'vitest'
import { nativeAcceptanceApi } from './nativeAcceptanceApi'
const get=vi.hoisted(()=>vi.fn())
vi.mock('@/config/axios',()=>({default:{get}}))
it('reads live draft attachment counts without changing historical snapshots',async()=>{
 get.mockResolvedValueOnce([{artifactId:'91'},{artifactId:'92'}])
 const draft={id:21,reportStatus:'DRAFT',attachments:[]},history={id:22,reportStatus:'EFFECTIVE',attachments:[{id:71}]}
 const original={getReportVersions:vi.fn(async()=>[draft,history])}
 const client=nativeAcceptanceApi({api:{base:'/api/v1/pms/acceptance-business'}} as any,original as any)
 const rows=await client.getReportVersions(11)
 expect(get).toHaveBeenCalledTimes(1);expect(get).toHaveBeenCalledWith({url:'/api/v1/pms/acceptance-business/11/reports/21/files'})
 expect(rows[0]).toMatchObject({draftAttachmentCount:2,attachments:[]})
 expect(rows[1]).toBe(history);expect(draft).not.toHaveProperty('draftAttachmentCount')
})
it('propagates failed live draft reads instead of declaring zero uploaded files',async()=>{
 get.mockReset();get.mockRejectedValueOnce(new Error('files unavailable'))
 const client=nativeAcceptanceApi({api:{base:'/test'}} as any,{getReportVersions:async()=>[{id:21,reportStatus:'DRAFT',attachments:[]}]} as any)
 await expect(client.getReportVersions(11)).rejects.toThrow('files unavailable')
})
