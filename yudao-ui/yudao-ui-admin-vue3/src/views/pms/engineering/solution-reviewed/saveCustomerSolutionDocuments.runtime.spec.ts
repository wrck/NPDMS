import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {saveCustomerSolutionDocuments,resolveCustomerSolutionDocument,customerSolutionDocumentUrls,customerSolutionDocumentName,downloadCustomerSolutionDocument} from './saveCustomerSolutionDocuments'
import {initializeUpload,completeUpload} from '@/api/pms/platform/file'
import {getDeliveryTypes} from '@/api/pms/platform/delivery'
import request from '@/config/axios'
afterEach(()=>vi.unstubAllGlobals())
import type {DeliveryUploadAttempt} from '@/components/DeliveryArtifact/uploadDeliveryFile'
vi.mock('@/config/axios',()=>({default:{post:vi.fn(),get:vi.fn()}}))
vi.mock('@/api/pms/platform/file',()=>({initializeUpload:vi.fn(),completeUpload:vi.fn()}))
vi.mock('@/api/pms/platform/delivery',()=>({getDeliveryTypes:vi.fn()}))
const file={name:'customer.pdf',type:'application/pdf',size:12} as File
beforeEach(()=>{vi.clearAllMocks();vi.mocked(getDeliveryTypes).mockResolvedValue([{typeCode:'IMPLEMENTATION_PLAN',category:'BUSINESS_DOCUMENT',enabled:true}] as any);vi.mocked(initializeUpload).mockResolvedValue({artifactId:31,sessionId:'session'} as any);vi.mocked(completeUpload).mockResolvedValue({referenceId:41} as any);vi.mocked(request.post).mockResolvedValue({version:3,customerPlanUrl:'/api/v1/pms/solutions/9/customer-files/51/customer.pdf',materialIds:[51]})})
it('uploads only to saved native solution root and binds through atomic native Owner command',async()=>{await saveCustomerSolutionDocuments({id:9,version:2},[file]);expect(initializeUpload).toHaveBeenCalledWith(expect.objectContaining({ownerContext:'PLT',objectType:'DELIVERY_MATERIAL',objectId:'SOL:solution:9',purposeCode:'IMPLEMENTATION_PLAN'}),expect.any(String));expect(request.post).toHaveBeenCalledWith({url:'/api/v1/pms/solutions/9/customer-files',data:{expectedVersion:2,referenceIds:[41]}})})
it('failed registration preserves completed references; retry never repeats byte upload',async()=>{const attempts:DeliveryUploadAttempt[]=[];vi.mocked(request.post).mockRejectedValueOnce(new Error('bind unavailable'));await expect(saveCustomerSolutionDocuments({id:9,version:2},[file],attempts)).rejects.toThrow('bind unavailable');expect(attempts[0].referenceId).toBe(41);await saveCustomerSolutionDocuments({id:9,version:2},[file],attempts);expect(initializeUpload).toHaveBeenCalledTimes(1);expect(completeUpload).toHaveBeenCalledTimes(1);expect(request.post).toHaveBeenCalledTimes(2)})
it('failed byte completion retains same session and idempotency key',async()=>{const attempts:DeliveryUploadAttempt[]=[];vi.mocked(completeUpload).mockRejectedValueOnce(new Error('byte failure'));await expect(saveCustomerSolutionDocuments({id:9,version:2},[file],attempts)).rejects.toThrow('byte failure');const key=attempts[0].completeKey;await saveCustomerSolutionDocuments({id:9,version:2},[file],attempts);expect(initializeUpload).toHaveBeenCalledTimes(1);expect(completeUpload).toHaveBeenLastCalledWith(31,'session',file,key)})
it('different root cannot reuse another root completed reference',async()=>{const attempts:DeliveryUploadAttempt[]=[];await saveCustomerSolutionDocuments({id:9,version:2},[file],attempts);await saveCustomerSolutionDocuments({id:10,version:2},[file],attempts);expect(initializeUpload).toHaveBeenCalledTimes(2);expect(initializeUpload).toHaveBeenLastCalledWith(expect.objectContaining({objectId:'SOL:solution:10'}),expect.any(String))})
it('no draft identity cannot upload before root save',async()=>{await expect(saveCustomerSolutionDocuments({id:0,version:0},[file])).rejects.toThrow();expect(initializeUpload).not.toHaveBeenCalled();expect(request.post).not.toHaveBeenCalled()})
it('unsupported or excessive file does not reach upload',async()=>{await expect(saveCustomerSolutionDocuments({id:9,version:2},[{name:'bad.exe',type:'',size:12} as File])).rejects.toThrow();expect(initializeUpload).not.toHaveBeenCalled()})
it('legacy immutable URL remains unchanged while native pointer requires authenticated short ticket',async()=>{expect(await resolveCustomerSolutionDocument('https://legacy/old.pdf')).toBe('https://legacy/old.pdf');expect(request.get).not.toHaveBeenCalled();vi.mocked(request.get).mockResolvedValue('/ticket');expect(await resolveCustomerSolutionDocument('/api/v1/pms/solutions/9/customer-files/51/customer.pdf')).toBe('/ticket')})

it.each(['customer,plan.txt','customer plan.txt','客户方案.txt','customer%2Cplan.txt'])('keeps native document %s as one display/download locator',async(name)=>{
 const locator='/api/v1/pms/solutions/9/customer-files/51/'+encodeURIComponent(name)
 expect(customerSolutionDocumentUrls(locator)).toEqual([locator])
 expect(customerSolutionDocumentName(locator)).toBe(name)
 vi.mocked(request.get).mockResolvedValueOnce('https://local/ticket')
 vi.stubGlobal('window',{open:vi.fn()})
 const opened=vi.spyOn(window,'open').mockImplementation(()=>null)
 await downloadCustomerSolutionDocument(locator)
 expect(request.get).toHaveBeenLastCalledWith({url:locator})
 expect(opened).toHaveBeenLastCalledWith('https://local/ticket','_blank','noopener')
 opened.mockRestore()
})
it('preserves legacy comma-separated links and passes their download URLs unchanged',async()=>{
 const links=['https://legacy/one%20file.pdf','https://legacy/two%25file.pdf']
 expect(customerSolutionDocumentUrls(links.join(','))).toEqual(links)
 vi.stubGlobal('window',{open:vi.fn()})
 const opened=vi.spyOn(window,'open').mockImplementation(()=>null)
 for(const link of links){expect(customerSolutionDocumentName(link)).toBe('');await downloadCustomerSolutionDocument(link);expect(opened).toHaveBeenLastCalledWith(link,'_blank','noopener')}
 opened.mockRestore()
})
