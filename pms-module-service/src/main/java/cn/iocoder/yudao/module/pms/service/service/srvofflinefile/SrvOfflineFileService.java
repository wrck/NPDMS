package cn.iocoder.yudao.module.pms.service.service.srvofflinefile;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFilePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvofflinefile.vo.SrvOfflineFileSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvofflinefile.SrvOfflineFileDO;

/**
 * 离线巡检文件 Service 接口
 */
public interface SrvOfflineFileService {

    /**
     * 创建离线巡检文件
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvOfflineFile(SrvOfflineFileSaveReqVO createReqVO);

    /**
     * 更新离线巡检文件
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvOfflineFile(SrvOfflineFileSaveReqVO updateReqVO);

    /**
     * 删除离线巡检文件
     *
     * @param id 编号
     */
    void deleteSrvOfflineFile(Long id);

    /**
     * 获得离线巡检文件分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvOfflineFileDO> getSrvOfflineFilePage(SrvOfflineFilePageReqVO pageReqVO);

    /**
     * 获得离线巡检文件
     *
     * @param id 编号
     * @return 离线巡检文件
     */
    SrvOfflineFileDO getSrvOfflineFile(Long id);

    /**
     * 开始解析（0待解析 → 1解析中）
     *
     * @param id 编号
     */
    void startParse(Long id);

    /**
     * 解析成功（1解析中 → 2解析成功）
     *
     * @param id 编号
     */
    void parseSuccess(Long id);

    /**
     * 解析失败（1解析中 → 3解析失败）
     *
     * @param id 编号
     */
    void parseFailed(Long id);

}
