package cn.iocoder.yudao.module.pms.service.service.srvreport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportPageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvreport.vo.SrvReportSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvreport.SrvReportDO;

/**
 * 巡检报告 Service 接口
 */
public interface SrvReportService {

    /**
     * 创建巡检报告
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvReport(SrvReportSaveReqVO createReqVO);

    /**
     * 更新巡检报告
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvReport(SrvReportSaveReqVO updateReqVO);

    /**
     * 删除巡检报告
     *
     * @param id 编号
     */
    void deleteSrvReport(Long id);

    /**
     * 获得巡检报告分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvReportDO> getSrvReportPage(SrvReportPageReqVO pageReqVO);

    /**
     * 获得巡检报告
     *
     * @param id 编号
     * @return 巡检报告
     */
    SrvReportDO getSrvReport(Long id);

    /**
     * 生成报告（0草稿 → 1已生成）
     *
     * @param id 编号
     */
    void generateSrvReport(Long id);

    /**
     * 归档报告（1已生成 → 2已归档）
     *
     * @param id 编号
     */
    void archiveSrvReport(Long id);

}
