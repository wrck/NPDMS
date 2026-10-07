package cn.iocoder.yudao.module.pms.project.api.workbinding.result;

import java.util.List;

/** Shared Owner routing for a project-authorized caller; does not grant user access. */
public interface ProjectBusinessResultEvidenceApi {
    default BusinessResultSource.DeliveryIdentity deliveryIdentity(BusinessResultSource.Query query) { return null; }
    List<BusinessResultSource.Descriptor> types();
    BusinessResultInventorySource.InventoryPage candidates(BusinessResultInventorySource.InventoryQuery query);
    BusinessResultSource.Observation lockAndInspect(BusinessResultSource.Query query);
}
