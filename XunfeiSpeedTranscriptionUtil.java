package com.multimodal.interview.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

public class XunfeiSpeedTranscriptionUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 上传音频文件
    public static Map<String, Object> uploadFile(String uploadUrl, MultipartFile file, Map<String, String> params, String apiKey, String apiSecret) throws Exception {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String CRLF = "\r\n";
        URL url = new URL(uploadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        // 生成date
        String date = getGMTDate();
        conn.setRequestProperty("date", date);
        conn.setRequestProperty("host", url.getHost());
        // 构建body
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append("--").append(boundary).append(CRLF);
            sb.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"").append(CRLF).append(CRLF);
            sb.append(entry.getValue()).append(CRLF);
        }
        sb.append("--").append(boundary).append(CRLF);
        sb.append("Content-Disposition: form-data; name=\"data\"; filename=\"").append(file.getOriginalFilename()).append("\"").append(CRLF);
        sb.append("Content-Type: ").append(file.getContentType()).append(CRLF).append(CRLF);
        byte[] fileBytes = file.getBytes();
        byte[] bodyPrefix = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bodySuffix = (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);
        // digest
        byte[] bodyForDigest = new byte[bodyPrefix.length + fileBytes.length + bodySuffix.length];
        System.arraycopy(bodyPrefix, 0, bodyForDigest, 0, bodyPrefix.length);
        System.arraycopy(fileBytes, 0, bodyForDigest, bodyPrefix.length, fileBytes.length);
        System.arraycopy(bodySuffix, 0, bodyForDigest, bodyPrefix.length + fileBytes.length, bodySuffix.length);
        String digest = "SHA-256=" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bodyForDigest));
        conn.setRequestProperty("digest", digest);
        // authorization
        String requestLine = "POST /file/upload HTTP/1.1";
        String signatureOrigin = String.format("host: %s\ndate: %s\n%s\ndigest: %s", url.getHost(), date, requestLine, digest);
        String signature = signHmacSha256(signatureOrigin, apiSecret);
        String authorization = String.format("api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line digest\", signature=\"%s\"", apiKey, signature);
        conn.setRequestProperty("authorization", authorization);
        // 写入body
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(bodyPrefix);
            os.write(fileBytes);
            os.write(bodySuffix);
        }
        // 读取响应
        int code = conn.getResponseCode();
        InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
        JsonNode resp = objectMapper.readTree(is);
        if (resp.has("code") && resp.get("code").asInt() == 0) {
            Map<String, Object> result = new HashMap<>();
            result.put("url", resp.path("data").path("url").asText());
            return result;
        } else {
            throw new RuntimeException("上传失败: " + resp.toString());
        }
    }

    // 创建转写任务
    public static Map<String, Object> createTask(String createTaskUrl, String appId, String apiKey, String apiSecret, String requestId, String audioUrl) throws Exception {
        URL url = new URL(createTaskUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/json");
        String date = getGMTDate();
        conn.setRequestProperty("date", date);
        conn.setRequestProperty("host", url.getHost());
        // body
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> common = new HashMap<>();
        common.put("app_id", appId);
        Map<String, Object> business = new HashMap<>();
        business.put("request_id", requestId);
        business.put("language", "zh_cn");
        business.put("domain", "pro_ost_ed");
        business.put("accent", "mandarin");
        Map<String, Object> data = new HashMap<>();
        data.put("audio_url", audioUrl);
        data.put("audio_src", "http");
        data.put("format", "audio/L16;rate=16000");
        data.put("encoding", "raw");
        body.put("common", common);
        body.put("business", business);
        body.put("data", data);
        String bodyStr = objectMapper.writeValueAsString(body);
        // digest
        String digest = "SHA-256=" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bodyStr.getBytes(StandardCharsets.UTF_8)));
        conn.setRequestProperty("digest", digest);
        // authorization
        String requestLine = "POST /v2/ost/pro_create HTTP/1.1";
        String signatureOrigin = String.format("host: %s\ndate: %s\n%s\ndigest: %s", url.getHost(), date, requestLine, digest);
        String signature = signHmacSha256(signatureOrigin, apiSecret);
        String authorization = String.format("api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line digest\", signature=\"%s\"", apiKey, signature);
        conn.setRequestProperty("authorization", authorization);
        // 写入body
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(bodyStr.getBytes(StandardCharsets.UTF_8));
        }
        // 读取响应
        int code = conn.getResponseCode();
        InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
        JsonNode resp = objectMapper.readTree(is);
        if (resp.has("code") && resp.get("code").asInt() == 0) {
            Map<String, Object> result = new HashMap<>();
            result.put("task_id", resp.path("data").path("task_id").asText());
            return result;
        } else {
            throw new RuntimeException("创建任务失败: " + resp.toString());
        }
    }

    // 轮询查询任务结果
    public static String pollQueryTask(String queryTaskUrl, String appId, String apiKey, String apiSecret, String taskId) throws Exception {
        int maxTry = 30; // 最多轮询30次
        int interval = 2000; // 每2秒查询一次
        for (int i = 0; i < maxTry; i++) {
            String result = queryTask(queryTaskUrl, appId, apiKey, apiSecret, taskId);
            if (result != null) {
                return result;
            }
            Thread.sleep(interval);
        }
        throw new RuntimeException("转写超时");
    }

    // 查询任务
    public static String queryTask(String queryTaskUrl, String appId, String apiKey, String apiSecret, String taskId) throws Exception {
        URL url = new URL(queryTaskUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/json");
        String date = getGMTDate();
        conn.setRequestProperty("date", date);
        conn.setRequestProperty("host", url.getHost());
        // body
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> common = new HashMap<>();
        common.put("app_id", appId);
        Map<String, Object> business = new HashMap<>();
        business.put("task_id", taskId);
        body.put("common", common);
        body.put("business", business);
        String bodyStr = objectMapper.writeValueAsString(body);
        // digest
        String digest = "SHA-256=" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bodyStr.getBytes(StandardCharsets.UTF_8)));
        conn.setRequestProperty("digest", digest);
        // authorization
        String requestLine = "POST /v2/ost/query HTTP/1.1";
        String signatureOrigin = String.format("host: %s\ndate: %s\n%s\ndigest: %s", url.getHost(), date, requestLine, digest);
        String signature = signHmacSha256(signatureOrigin, apiSecret);
        String authorization = String.format("api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line digest\", signature=\"%s\"", apiKey, signature);
        conn.setRequestProperty("authorization", authorization);
        // 写入body
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(bodyStr.getBytes(StandardCharsets.UTF_8));
        }
        // 读取响应
        int code = conn.getResponseCode();
        InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
        JsonNode resp = objectMapper.readTree(is);
        if (resp.has("code") && resp.get("code").asInt() == 0) {
            String status = resp.path("data").path("task_status").asText();
            if ("3".equals(status) || "4".equals(status)) {
                // 解析文本
                JsonNode resultNode = resp.path("data").path("result");
                if (resultNode.has("lattice")) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode lattice : resultNode.path("lattice")) {
                        JsonNode wsArr = lattice.path("json_1best").path("st").path("rt").get(0).path("ws");
                        for (JsonNode ws : wsArr) {
                            for (JsonNode cw : ws.path("cw")) {
                                sb.append(cw.path("w").asText());
                            }
                        }
                    }
                    return sb.toString();
                }
            }
        }
        return null;
    }

    // HMAC-SHA256签名并Base64
    private static String signHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    // 获取GMT时间
    private static String getGMTDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }
} 
