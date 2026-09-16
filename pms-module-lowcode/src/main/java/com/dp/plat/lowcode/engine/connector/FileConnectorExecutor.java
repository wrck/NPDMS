package com.dp.plat.lowcode.engine.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * 文件连接器执行器（批次4-T5）。
 *
 * <p>通过 SFTP 协议与远程文件服务器交互，支持 UPLOAD/DOWNLOAD/LIST/DELETE 四种操作。
 * 适用于与外部系统的文件交换场景（如导出报表到 SFTP、拉取对账文件等）。</p>
 *
 * <p>config JSON 结构：
 * <pre>{@code
 * {
 *   "operation": "UPLOAD|DOWNLOAD|LIST|DELETE",
 *   "host": "sftp.example.com",
 *   "port": 22,
 *   "username": "user",
 *   "password": "pass",
 *   "remotePath": "/path/to/dir",
 *   "localPath": "/tmp/file.txt",   // UPLOAD: 本地源路径; DOWNLOAD: 本地目标路径
 *   "fileName": "report.csv",       // DOWNLOAD/DELETE: 远程文件名
 *   "timeoutMillis": 10000
 * }
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileConnectorExecutor {

    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public ConnectorResult execute(String configJson, Map<String, Object> params) {
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
            String operation = ((String) config.getOrDefault("operation", "LIST")).toUpperCase();

            String host = (String) config.getOrDefault("host", "localhost");
            int port = ((Number) config.getOrDefault("port", 22)).intValue();
            String username = (String) config.getOrDefault("username", "anonymous");
            String password = (String) config.getOrDefault("password", "");
            int timeoutMillis = ((Number) config.getOrDefault("timeoutMillis", 10000)).intValue();

            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(timeoutMillis);
            session.connect(timeoutMillis);

            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(timeoutMillis);

            try {
                return switch (operation) {
                    case "UPLOAD" -> doUpload(channel, config, params);
                    case "DOWNLOAD" -> doDownload(channel, config, params);
                    case "LIST" -> doList(channel, config, params);
                    case "DELETE" -> doDelete(channel, config, params);
                    default -> ConnectorResult.error(400, "不支持的文件操作: " + operation);
                };
            } finally {
                channel.disconnect();
                session.disconnect();
            }
        } catch (Exception e) {
            log.error("文件连接器执行失败", e);
            return ConnectorResult.error(500, e.getMessage());
        }
    }

    // ==================== SFTP 操作 ====================

    @SuppressWarnings("unchecked")
    private ConnectorResult doUpload(ChannelSftp channel, Map<String, Object> config,
                                       Map<String, Object> params) throws Exception {
        String remotePath = (String) config.get("remotePath");
        String localPath = (String) config.get("localPath");
        if (remotePath == null || localPath == null) {
            return ConnectorResult.error(400, "UPLOAD 需指定 remotePath 和 localPath");
        }
        try (FileInputStream fis = new FileInputStream(localPath)) {
            channel.put(fis, remotePath);
        }
        log.info("SFTP 上传完成: {} → {}", localPath, remotePath);
        return ConnectorResult.ok(Map.of(
                "operation", "UPLOAD",
                "remotePath", remotePath,
                "localPath", localPath,
                "status", "SUCCESS"));
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult doDownload(ChannelSftp channel, Map<String, Object> config,
                                         Map<String, Object> params) throws Exception {
        String remotePath = (String) config.get("remotePath");
        String localPath = (String) config.get("localPath");
        if (remotePath == null || localPath == null) {
            return ConnectorResult.error(400, "DOWNLOAD 需指定 remotePath 和 localPath");
        }
        try (FileOutputStream fos = new FileOutputStream(localPath)) {
            channel.get(remotePath, fos);
        }
        log.info("SFTP 下载完成: {} → {}", remotePath, localPath);
        return ConnectorResult.ok(Map.of(
                "operation", "DOWNLOAD",
                "remotePath", remotePath,
                "localPath", localPath,
                "status", "SUCCESS"));
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult doList(ChannelSftp channel, Map<String, Object> config,
                                     Map<String, Object> params) throws SftpException {
        String remoteDir = (String) config.getOrDefault("remotePath", ".");
        Vector<ChannelSftp.LsEntry> entries = channel.ls(remoteDir);
        List<Map<String, Object>> files = new ArrayList<>();
        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) continue;
            Map<String, Object> file = new HashMap<>();
            file.put("name", name);
            file.put("size", entry.getAttrs().getSize());
            file.put("isDirectory", entry.getAttrs().isDir());
            file.put("modifiedTime", entry.getAttrs().getMTime());
            file.put("permissions", entry.getAttrs().getPermissionsString());
            files.add(file);
        }
        return ConnectorResult.ok(Map.of(
                "operation", "LIST",
                "remotePath", remoteDir,
                "fileCount", files.size(),
                "files", files));
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult doDelete(ChannelSftp channel, Map<String, Object> config,
                                       Map<String, Object> params) throws SftpException {
        String remotePath = (String) config.get("remotePath");
        if (remotePath == null) {
            return ConnectorResult.error(400, "DELETE 需指定 remotePath");
        }
        channel.rm(remotePath);
        log.info("SFTP 删除完成: {}", remotePath);
        return ConnectorResult.ok(Map.of(
                "operation", "DELETE",
                "remotePath", remotePath,
                "status", "SUCCESS"));
    }
}
