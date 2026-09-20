package cn.iocoder.yudao.module.pms.service.service.srvofflinefile;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFilePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFileSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvofflinefile.SrvOfflineFileRetiredDO;

/**
 * 离线巡检文件 Service 接口
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Deprecated
public interface SrvOfflineFileService {

    /**
     * 创建离线巡检文件
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvOfflineFileRetired(SrvOfflineFileSaveReqVO createReqVO);

    /**
     * 更新离线巡检文件
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvOfflineFileRetired(SrvOfflineFileSaveReqVO updateReqVO);

    /**
     * 删除离线巡检文件
     *
     * @param id 编号
     */
    void deleteSrvOfflineFileRetired(Long id);

    /**
     * 获得离线巡检文件分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvOfflineFileRetiredDO> getSrvOfflineFilePageRetired(SrvOfflineFilePageReqVO pageReqVO);

    /**
     * 获得离线巡检文件
     *
     * @param id 编号
     * @return 离线巡检文件
     */
    SrvOfflineFileRetiredDO getSrvOfflineFileRetired(Long id);

    /**
     * 开始解析（0待解析 → 1解析中）
     *
     * @param id 编号
     */
    void startParseRetired(Long id);

    /**
     * 解析成功（1解析中 → 2解析成功）
     *
     * @param id 编号
     */
    void parseSuccessRetired(Long id);

    /**
     * 解析失败（1解析中 → 3解析失败）
     *
     * @param id 编号
     */
    void parseFailedRetired(Long id);

}
