package cn.iocoder.yudao.module.pms.cutover.controller.business.checklist.vo;

import cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverExternalChecklistService;
import java.time.LocalDateTime;
import java.util.List;

public final class CutoverExternalChecklistResponses {
    private CutoverExternalChecklistResponses() { }
    public record State(Long checklistId, Long checklistFactVersion, List<Device> devices, List<Item> items) {
        public static State from(CutoverExternalChecklistService.State value) {
            return new State(value.checklistId(), value.checklistFactVersion(),
                    value.devices().stream().map(Device::from).toList(), value.items().stream().map(Item::from).toList());
        }
    }
    public record Device(Long deviceId, String sn, String productModel, String currentSoftwareVersion, String targetSoftwareVersion) {
        static Device from(CutoverExternalChecklistService.DeviceView value) {
            return new Device(value.deviceId(), value.sn(), value.productModel(), value.currentSoftwareVersion(), value.targetSoftwareVersion());
        }
    }
    public record Query(Long id, Long deviceId, String versionContext, String softwareVersion, String productModel,
                        LocalDateTime startTime, LocalDateTime finishTime, Boolean succeeded, Integer returnedRowCount,
                        String failureCode, String failureDetail) {
        static Query from(CutoverExternalChecklistService.QueryView value) {
            return new Query(value.id(), value.deviceId(), value.versionContext(), value.softwareVersion(), value.productModel(),
                    value.startTime(), value.finishTime(), value.succeeded(), value.returnedRowCount(), value.failureCode(), value.failureDetail());
        }
    }
    public record Bulletin(Long sourceBulletinRowId, Long queryId, String number, String subject, String description,
                           String solution, Boolean involved, String measures) {
        static Bulletin from(CutoverExternalChecklistService.BulletinView value) {
            return new Bulletin(value.sourceBulletinRowId(), value.queryId(), value.number(), value.subject(), value.description(),
                    value.solution(), value.involved(), value.measures());
        }
    }
    public record Item(Long itemId, String stableItemKey, String interfaceCode, boolean providerAvailable,
                       boolean manualFallback, Long resultId, Integer resultVersion, String resultSourceCode,
                       boolean feedbackSaved, boolean contextCurrent, List<Long> selectedQueryIds,
                       List<Query> queries, List<Bulletin> bulletins) {
        static Item from(CutoverExternalChecklistService.ItemView value) {
            return new Item(value.itemId(), value.stableItemKey(), value.interfaceCode(), value.providerAvailable(),
                    value.manualFallback(), value.resultId(), value.resultVersion(), value.resultSourceCode(),
                    value.feedbackSaved(), value.contextCurrent(), List.copyOf(value.selectedQueryIds()),
                    value.queries().stream().map(Query::from).toList(), value.bulletins().stream().map(Bulletin::from).toList());
        }
    }
    public record LoadResult(Long checklistId, Long checklistFactVersion, Long resultId, Integer resultVersion,
                             boolean selected, List<Query> queries) {
        public static LoadResult from(CutoverExternalChecklistService.LoadResult value) {
            return new LoadResult(value.checklistId(), value.checklistFactVersion(), value.resultId(), value.resultVersion(),
                    value.selected(), value.queries().stream().map(Query::from).toList());
        }
    }
}
