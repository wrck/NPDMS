package cn.iocoder.yudao.module.pms.asset.domain.assembly;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DeviceAssemblyRules {

    public boolean canAssemble(
            String parentDeviceSn,
            String childDeviceSn,
            List<DeviceAssemblyEdge> currentEdges) {
        if (Objects.equals(parentDeviceSn, childDeviceSn)) {
            return false;
        }
        Map<String, List<String>> childrenByParent = currentEdges.stream()
                .collect(Collectors.groupingBy(
                        DeviceAssemblyEdge::parentDeviceSn,
                        Collectors.mapping(DeviceAssemblyEdge::childDeviceSn, Collectors.toList())));
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(childDeviceSn);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            if (Objects.equals(current, parentDeviceSn)) {
                return false;
            }
            pending.addAll(childrenByParent.getOrDefault(current, List.of()));
        }
        return true;
    }
}
