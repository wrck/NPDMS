package com.dp.plat.lowcode.engine.microflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微流图渲染服务（批次3-T6）。
 *
 * <p>将微流 {@code definition} JSON（nodes + edges）渲染为 SVG 或 PNG 图像，
 * 用于流程图导出、文档嵌入、缩略图预览等场景。</p>
 *
 * <p>渲染规则：
 * <ul>
 *   <li>节点形状按类型区分：
 *     <ul>
 *       <li>START/END → 圆角矩形（绿/红）</li>
 *       <li>CONDITION/LOOP → 菱形（黄/紫）</li>
 *       <li>ASSIGN/RETURN/THROW_EXCEPTION → 矩形（蓝/灰/橙）</li>
 *       <li>CALL_* → 矩形（青）</li>
 *     </ul>
 *   </li>
 *   <li>边：带箭头的折线，CONDITION 的 true/false 边用绿/红标注</li>
 *   <li>布局：优先使用节点自带的 x/y 坐标；缺失时按 DAG 拓扑序自动纵向布局</li>
 *   <li>SVG：纯字符串拼接，无外部依赖</li>
 *   <li>PNG：Java2D Graphics2D 渲染，无外部依赖</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MicroflowDiagramService {

    private final ObjectMapper objectMapper;

    /** 默认节点宽度 */
    private static final int NODE_W = 140;
    /** 默认节点高度 */
    private static final int NODE_H = 50;
    /** 自动布局纵向间距 */
    private static final int GAP_Y = 80;
    /** 自动布局横向间距 */
    private static final int GAP_X = 200;
    /** 画布内边距 */
    private static final int PADDING = 40;

    /** 节点类型 → 颜色映射 */
    private static final Map<String, NodeStyle> NODE_STYLES = new HashMap<>();

    static {
        NODE_STYLES.put("START", new NodeStyle("#4caf50", "#2e7d32", "circle"));
        NODE_STYLES.put("END", new NodeStyle("#f44336", "#c62828", "circle"));
        NODE_STYLES.put("CONDITION", new NodeStyle("#ff9800", "#ef6c00", "diamond"));
        NODE_STYLES.put("LOOP", new NodeStyle("#9c27b0", "#6a1b9a", "diamond"));
        NODE_STYLES.put("ASSIGN", new NodeStyle("#2196f3", "#1565c0", "rect"));
        NODE_STYLES.put("RETURN", new NodeStyle("#607d8b", "#37474f", "rect"));
        NODE_STYLES.put("THROW_EXCEPTION", new NodeStyle("#ff5722", "#d84315", "rect"));
        NODE_STYLES.put("CALL_SERVICE", new NodeStyle("#00bcd4", "#00838f", "rect"));
        NODE_STYLES.put("CALL_MICROFLOW", new NodeStyle("#00bcd4", "#00838f", "rect"));
        NODE_STYLES.put("CALL_RULE", new NodeStyle("#00bcd4", "#00838f", "rect"));
        NODE_STYLES.put("CALL_CONNECTOR", new NodeStyle("#00bcd4", "#00838f", "rect"));
    }

    private static final NodeStyle DEFAULT_STYLE = new NodeStyle("#9e9e9e", "#616161", "rect");

    /**
     * 渲染微流定义为 SVG 字符串。
     *
     * @param definitionJson 微流定义 JSON
     * @return SVG XML 字符串
     */
    public String renderSvg(String definitionJson) {
        DiagramData data = parse(definitionJson);
        return buildSvg(data);
    }

    /**
     * 渲染微流定义为 PNG 字节数组。
     *
     * @param definitionJson 微流定义 JSON
     * @return PNG 图像字节数组
     * @throws IOException 当图像编码失败时
     */
    public byte[] renderPng(String definitionJson) throws IOException {
        DiagramData data = parse(definitionJson);
        return buildPng(data);
    }

    // ==================== 解析 ====================

    private DiagramData parse(String definitionJson) {
        DiagramData data = new DiagramData();
        if (definitionJson == null || definitionJson.isBlank()) {
            return data;
        }
        try {
            JsonNode root = objectMapper.readTree(definitionJson);
            JsonNode nodesNode = root.get("nodes");
            JsonNode edgesNode = root.get("edges");

            Map<String, DiagramNode> nodeMap = new LinkedHashMap<>();
            if (nodesNode != null && nodesNode.isArray()) {
                for (JsonNode n : nodesNode) {
                    DiagramNode node = new DiagramNode();
                    node.id = text(n, "id", "");
                    node.type = text(n, "type", "");
                    node.label = text(n, "label", node.id);
                    node.x = n.has("x") ? n.get("x").asDouble() : null;
                    node.y = n.has("y") ? n.get("y").asDouble() : null;
                    nodeMap.put(node.id, node);
                    data.nodes.add(node);
                }
            }

            if (edgesNode != null && edgesNode.isArray()) {
                for (JsonNode e : edgesNode) {
                    DiagramEdge edge = new DiagramEdge();
                    edge.source = text(e, "source", "");
                    edge.target = text(e, "target", "");
                    edge.sourcePort = text(e, "sourcePort", "");
                    data.edges.add(edge);
                    // 记录邻接关系用于自动布局
                    nodeMap.getOrDefault(edge.source, new DiagramNode()).next.add(edge.target);
                }
            }

            // 若节点缺少坐标，执行自动布局
            boolean needLayout = data.nodes.stream().anyMatch(n -> n.x == null || n.y == null);
            if (needLayout) {
                autoLayout(data, nodeMap);
            }
        } catch (Exception e) {
            log.warn("解析微流 definition JSON 失败，返回空图: {}", e.getMessage());
        }
        return data;
    }

    /**
     * 自动布局：从 START 节点出发，按 BFS 层次分配坐标。
     * 缺少 START 时用首个节点。无坐标的节点按拓扑深度纵向排列。
     */
    private void autoLayout(DiagramData data, Map<String, DiagramNode> nodeMap) {
        DiagramNode start = data.nodes.stream()
                .filter(n -> "START".equals(n.type))
                .findFirst()
                .orElse(data.nodes.isEmpty() ? null : data.nodes.get(0));
        if (start == null) {
            return;
        }

        // BFS 分层
        Map<String, Integer> depth = new HashMap<>();
        List<String> queue = new ArrayList<>();
        queue.add(start.id);
        depth.put(start.id, 0);
        int maxDepth = 0;
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            int d = depth.get(cur);
            DiagramNode node = nodeMap.get(cur);
            if (node == null) continue;
            for (String next : node.next) {
                if (!depth.containsKey(next)) {
                    depth.put(next, d + 1);
                    maxDepth = Math.max(maxDepth, d + 1);
                    queue.add(next);
                }
            }
        }

        // 同层节点计数，用于横向错开
        Map<Integer, Integer> depthCount = new HashMap<>();
        for (DiagramNode n : data.nodes) {
            if (n.x != null && n.y != null) continue;
            int d = depth.getOrDefault(n.id, maxDepth + 1);
            int idx = depthCount.getOrDefault(d, 0);
            n.x = (double) (PADDING + idx * GAP_X);
            n.y = (double) (PADDING + d * (NODE_H + GAP_Y));
            depthCount.put(d, idx + 1);
        }
    }

    // ==================== SVG 渲染 ====================

    private String buildSvg(DiagramData data) {
        Bounds b = computeBounds(data);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"%d %d %d %d\">\n",
                b.width, b.height, b.minX - PADDING, b.minY - PADDING, b.width, b.height));
        sb.append("<rect x=\"").append(b.minX - PADDING).append("\" y=\"").append(b.minY - PADDING)
                .append("\" width=\"").append(b.width).append("\" height=\"").append(b.height)
                .append("\" fill=\"#ffffff\"/>\n");

        // 边（先画边再画节点，避免被遮挡）
        Map<String, DiagramNode> nodeMap = new HashMap<>();
        for (DiagramNode n : data.nodes) nodeMap.put(n.id, n);
        for (DiagramEdge edge : data.edges) {
            DiagramNode src = nodeMap.get(edge.source);
            DiagramNode tgt = nodeMap.get(edge.target);
            if (src == null || tgt == null) continue;
            double x1 = src.x + NODE_W / 2.0;
            double y1 = src.y + NODE_H;
            double x2 = tgt.x + NODE_W / 2.0;
            double y2 = tgt.y;
            String color = "#666666";
            String label = "";
            if (edge.sourcePort != null && edge.sourcePort.endsWith("-true")) {
                color = "#4caf50"; label = "Y";
            } else if (edge.sourcePort != null && edge.sourcePort.endsWith("-false")) {
                color = "#f44336"; label = "N";
            } else if (edge.sourcePort != null && edge.sourcePort.endsWith("-body")) {
                color = "#2196f3"; label = "loop";
            } else if (edge.sourcePort != null && edge.sourcePort.endsWith("-exit")) {
                color = "#9e9e9e"; label = "exit";
            }
            sb.append(String.format(
                    "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"1.5\" marker-end=\"url(#arrow)\"/>\n",
                    x1, y1, x2, y2, color));
            if (!label.isEmpty()) {
                double lx = (x1 + x2) / 2;
                double ly = (y1 + y2) / 2 - 4;
                sb.append(String.format(
                        "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" font-size=\"10\" fill=\"%s\" font-family=\"sans-serif\">%s</text>\n",
                        lx, ly, color, escapeXml(label)));
            }
        }

        // 箭头定义
        sb.append("<defs><marker id=\"arrow\" markerWidth=\"8\" markerHeight=\"8\" refX=\"7\" refY=\"4\" orient=\"auto\">\n");
        sb.append("<path d=\"M0,0 L8,4 L0,8 Z\" fill=\"#666666\"/>\n");
        sb.append("</marker></defs>\n");

        // 节点
        for (DiagramNode node : data.nodes) {
            NodeStyle style = NODE_STYLES.getOrDefault(node.type, DEFAULT_STYLE);
            String label = escapeXml(node.label != null && !node.label.isEmpty() ? node.label : node.id);
            String typeLabel = escapeXml(node.type);
            switch (style.shape) {
                case "circle" -> sb.append(String.format(
                        "<rect x=\"%.1f\" y=\"%.1f\" width=\"%d\" height=\"%d\" rx=\"%d\" ry=\"%d\" fill=\"%s\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                        node.x, node.y, NODE_W, NODE_H, NODE_H / 2, NODE_H / 2, style.fill, style.stroke));
                case "diamond" -> {
                    double cx = node.x + NODE_W / 2.0;
                    double cy = node.y + NODE_H / 2.0;
                    double dw = NODE_W / 2.0;
                    double dh = NODE_H / 2.0;
                    sb.append(String.format(
                            "<polygon points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f %.1f,%.1f\" fill=\"%s\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                            cx, cy - dh, cx + dw, cy, cx, cy + dh, cx - dw, cy, style.fill, style.stroke));
                }
                default -> sb.append(String.format(
                        "<rect x=\"%.1f\" y=\"%.1f\" width=\"%d\" height=\"%d\" fill=\"%s\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                        node.x, node.y, NODE_W, NODE_H, style.fill, style.stroke));
            }
            // 节点类型小标签（上方）
            sb.append(String.format(
                    "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" font-size=\"9\" fill=\"#666666\" font-family=\"sans-serif\">%s</text>\n",
                    node.x + NODE_W / 2.0, node.y - 6, typeLabel));
            // 节点主标签（居中）
            sb.append(String.format(
                    "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" dominant-baseline=\"middle\" font-size=\"13\" fill=\"#ffffff\" font-family=\"sans-serif\">%s</text>\n",
                    node.x + NODE_W / 2.0, node.y + NODE_H / 2.0, label));
        }

        sb.append("</svg>");
        return sb.toString();
    }

    // ==================== PNG 渲染（Java2D） ====================

    private byte[] buildPng(DiagramData data) throws IOException {
        Bounds b = computeBounds(data);
        int imgW = b.width + 2 * PADDING;
        int imgH = b.height + 2 * PADDING;
        int offsetX = PADDING - b.minX;
        int offsetY = PADDING - b.minY;

        BufferedImage image = new BufferedImage(Math.max(imgW, 1), Math.max(imgH, 1),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, imgW, imgH);

            Map<String, DiagramNode> nodeMap = new HashMap<>();
            for (DiagramNode n : data.nodes) nodeMap.put(n.id, n);

            // 边
            g.setStroke(new BasicStroke(1.5f));
            Font labelFont = g.getFont().deriveFont(10f);
            for (DiagramEdge edge : data.edges) {
                DiagramNode src = nodeMap.get(edge.source);
                DiagramNode tgt = nodeMap.get(edge.target);
                if (src == null || tgt == null) continue;
                int x1 = (int) (src.x + NODE_W / 2.0) + offsetX;
                int y1 = (int) (src.y + NODE_H) + offsetY;
                int x2 = (int) (tgt.x + NODE_W / 2.0) + offsetX;
                int y2 = (int) (double) tgt.y + offsetY;
                g.setColor(Color.GRAY);
                g.drawLine(x1, y1, x2, y2);
                drawArrowHead(g, x1, y1, x2, y2);

                // 边标签
                String portLabel = "";
                if (edge.sourcePort != null) {
                    if (edge.sourcePort.endsWith("-true")) portLabel = "Y";
                    else if (edge.sourcePort.endsWith("-false")) portLabel = "N";
                    else if (edge.sourcePort.endsWith("-body")) portLabel = "loop";
                    else if (edge.sourcePort.endsWith("-exit")) portLabel = "exit";
                }
                if (!portLabel.isEmpty()) {
                    g.setFont(labelFont);
                    g.drawString(portLabel, (x1 + x2) / 2 - 4, (y1 + y2) / 2 - 4);
                }
            }

            // 节点
            Font nodeFont = g.getFont().deriveFont(13f);
            Font typeFont = g.getFont().deriveFont(9f);
            for (DiagramNode node : data.nodes) {
                NodeStyle style = NODE_STYLES.getOrDefault(node.type, DEFAULT_STYLE);
                int nx = (int) (node.x + offsetX);
                int ny = (int) (node.y + offsetY);
                g.setColor(decodeColor(style.fill));
                g.setStroke(new BasicStroke(2f));
                switch (style.shape) {
                    case "circle" -> {
                        g.fillRoundRect(nx, ny, NODE_W, NODE_H, NODE_H, NODE_H);
                        g.setColor(decodeColor(style.stroke));
                        g.drawRoundRect(nx, ny, NODE_W, NODE_H, NODE_H, NODE_H);
                    }
                    case "diamond" -> {
                        int cx = nx + NODE_W / 2;
                        int cy = ny + NODE_H / 2;
                        Path2D diamond = new Path2D.Double();
                        diamond.moveTo(cx, cy - NODE_H / 2);
                        diamond.lineTo(cx + NODE_W / 2, cy);
                        diamond.lineTo(cx, cy + NODE_H / 2);
                        diamond.lineTo(cx - NODE_W / 2, cy);
                        diamond.closePath();
                        g.fill(diamond);
                        g.setColor(decodeColor(style.stroke));
                        g.draw(diamond);
                    }
                    default -> {
                        g.fillRect(nx, ny, NODE_W, NODE_H);
                        g.setColor(decodeColor(style.stroke));
                        g.drawRect(nx, ny, NODE_W, NODE_H);
                    }
                }
                // 类型小标签
                g.setFont(typeFont);
                g.setColor(Color.GRAY);
                g.drawString(node.type, nx + NODE_W / 2 - g.getFontMetrics().stringWidth(node.type) / 2, ny - 6);
                // 主标签
                String label = (node.label != null && !node.label.isEmpty()) ? node.label : node.id;
                g.setFont(nodeFont);
                g.setColor(Color.WHITE);
                FontMetrics fm = g.getFontMetrics();
                Rectangle2D textBounds = fm.getStringBounds(label, g);
                g.drawString(label, nx + NODE_W / 2 - (int) (textBounds.getWidth() / 2),
                        ny + NODE_H / 2 + fm.getAscent() / 2 - 2);
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private void drawArrowHead(Graphics2D g, int x1, int y1, int x2, int y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;
        double ux = dx / len;
        double uy = dy / len;
        int arrowLen = 8;
        int arrowW = 4;
        // 箭头起点（向后退 arrowLen）
        int bx = (int) (x2 - ux * arrowLen);
        int by = (int) (y2 - uy * arrowLen);
        // 垂直方向
        double px = -uy;
        double py = ux;
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x2, y2);
        arrow.lineTo(bx + px * arrowW, by + py * arrowW);
        arrow.lineTo(bx - px * arrowW, by - py * arrowW);
        arrow.closePath();
        g.fill(arrow);
    }

    // ==================== 辅助 ====================

    private Bounds computeBounds(DiagramData data) {
        if (data.nodes.isEmpty()) {
            return new Bounds(0, 0, 200, 100);
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (DiagramNode n : data.nodes) {
            if (n.x == null || n.y == null) continue;
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxX = Math.max(maxX, n.x + NODE_W);
            maxY = Math.max(maxY, n.y + NODE_H);
        }
        if (minX == Double.MAX_VALUE) {
            return new Bounds(0, 0, 200, 100);
        }
        return new Bounds((int) minX, (int) minY,
                (int) (maxX - minX) + 2 * PADDING, (int) (maxY - minY) + 2 * PADDING);
    }

    private Color decodeColor(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return Color.GRAY;
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String text(JsonNode node, String field, String def) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() ? v.asText() : def;
    }

    // ==================== 数据结构 ====================

    private static class DiagramData {
        List<DiagramNode> nodes = new ArrayList<>();
        List<DiagramEdge> edges = new ArrayList<>();
    }

    private static class DiagramNode {
        String id;
        String type;
        String label;
        Double x;
        Double y;
        List<String> next = new ArrayList<>();
    }

    private static class DiagramEdge {
        String source;
        String target;
        String sourcePort;
    }

    private static class NodeStyle {
        final String fill;
        final String stroke;
        final String shape;
        NodeStyle(String fill, String stroke, String shape) {
            this.fill = fill;
            this.stroke = stroke;
            this.shape = shape;
        }
    }

    private static class Bounds {
        final int minX;
        final int minY;
        final int width;
        final int height;
        Bounds(int minX, int minY, int width, int height) {
            this.minX = minX;
            this.minY = minY;
            this.width = width;
            this.height = height;
        }
    }
}
