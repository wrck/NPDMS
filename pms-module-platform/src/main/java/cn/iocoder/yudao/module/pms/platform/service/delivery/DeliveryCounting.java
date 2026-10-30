package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliverySubmissionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryTypeDO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数量计数口径（显式按计数单位）：
 * - 参与计数的基础集合：ACTIVE 材料中，未被任何非 CURRENT 提交"独占"的记录——
 *   只被已取代/已撤回提交引用的材料不再计数；从未提交过或被 CURRENT 提交引用的材料计数。
 * - MATERIAL：按材料记录数；FILE_VERSION：按（文件工件，版本号）去重——同一文件多处展示不重复满足；
 * - SUBMISSION：按要求当前 CURRENT 提交数。
 */
final class DeliveryCounting {

    record CountingInput(List<DeliveryMaterialDO> activeMaterials, List<DeliverySubmissionDO> submissions) {
    }

    static List<DeliveryMaterialDO> countedMaterials(CountingInput input) {
        Map<Long, Set<String>> statusesByMaterial = new HashMap<>();
        for (DeliverySubmissionDO submission : input.submissions()) {
            for (Long materialId : parseMaterialIds(submission)) {
                statusesByMaterial.computeIfAbsent(materialId, key -> new HashSet<>())
                        .add(submission.getStatus());
            }
        }
        List<DeliveryMaterialDO> counted = new ArrayList<>();
        for (DeliveryMaterialDO material : input.activeMaterials()) {
            Set<String> statuses = statusesByMaterial.get(material.getId());
            if (statuses == null || statuses.contains(DeliverySubmissionDO.STATUS_CURRENT)) {
                counted.add(material);
            }
        }
        return counted;
    }

    static List<Long> parseMaterialIds(DeliverySubmissionDO submission) {
        return JsonSupport.parseLongList(submission.getMaterialIdsJson());
    }

    static int count(CountingInput input, String unit) {
        List<DeliveryMaterialDO> counted = countedMaterials(input);
        Set<Long> countedIds = counted.stream().map(DeliveryMaterialDO::getId).collect(Collectors.toSet());
        return switch (unit) {
            case DeliveryTypeDO.COUNTING_MATERIAL -> counted.size();
            case DeliveryTypeDO.COUNTING_FILE_VERSION -> counted.stream()
                    .filter(material -> material.getFileArtifactId() != null && material.getFileVersionNo() != null)
                    .map(material -> material.getFileArtifactId() + ":" + material.getFileVersionNo())
                    .collect(Collectors.toSet()).size();
            case DeliveryTypeDO.COUNTING_SUBMISSION -> (int) input.submissions().stream()
                    .filter(submission -> DeliverySubmissionDO.STATUS_CURRENT.equals(submission.getStatus()))
                    .filter(submission -> parseMaterialIds(submission).stream()
                            .anyMatch(countedIds::contains))
                    .count();
            default -> throw new IllegalArgumentException("unsupported counting unit: " + unit);
        };
    }

    private DeliveryCounting() {
    }
}
