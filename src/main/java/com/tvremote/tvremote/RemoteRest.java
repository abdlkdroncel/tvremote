package com.tvremote.tvremote;

import dadb.AdbShellResponse;
import dadb.Dadb;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/remote")
public class RemoteRest {

    private static final Pattern VOLUME_PATTERN =
            Pattern.compile("volume is (\\d+) in range \\[(\\d+)\\.\\.(\\d+)\\]");

    private final TvSession session;

    public RemoteRest(TvSession session) {
        this.session = session;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("connected", session.isConnected());
        if (session.isConnected()) {
            body.put("host", session.getHost());
            body.put("port", session.getPort());
        }
        return body;
    }

    @PostMapping("/api/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "")).trim();
        int port;
        try {
            port = Integer.parseInt(String.valueOf(body.getOrDefault("port", "5555")).trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Port sayı olmalı"));
        }

        try {
            String model = session.connect(host, port);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("status", "ok");
            ok.put("host", session.getHost());
            ok.put("port", session.getPort());
            ok.put("model", model);
            return ResponseEntity.ok(ok);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "TV'ye bağlanılamadı: " + e.getMessage()));
        }
    }

    @PostMapping("/api/disconnect")
    public Map<String, String> disconnect() {
        session.disconnect();
        return Map.of("status", "ok");
    }

    @PostMapping("/api/volume/{value}")
    public ResponseEntity<Map<String, String>> setVolume(@PathVariable int value) {
        if (value < 0 || value > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ses 0-100 arasında olmalı"));
        }

        ResponseEntity<Map<String, String>> offline = requireSession();
        if (offline != null) return offline;

        try (Dadb dadb = session.open()) {
            String out = dadb.shell("cmd media_session volume --stream 3 --get").getAllOutput();
            Matcher m = VOLUME_PATTERN.matcher(out);
            if (!m.find()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Mevcut ses okunamadı: " + out));
            }
            int current = Integer.parseInt(m.group(1));
            int max = Integer.parseInt(m.group(3));

            int target = Math.round(value * max / 100f);
            int diff = target - current;

            if (diff != 0) {
                String keyCode = diff > 0 ? "24" : "25";
                String keys = String.join(" ", Collections.nCopies(Math.abs(diff), keyCode));
                dadb.shell("input keyevent " + keys);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "TV'ye ulaşılamadı: " + e.getMessage()));
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/api/key/{name}")
    public ResponseEntity<Map<String, String>> key(@PathVariable String name) {
        Key key;
        try {
            key = Key.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bilinmeyen tuş: " + name));
        }

        ResponseEntity<Map<String, String>> offline = requireSession();
        if (offline != null) return offline;

        try (Dadb dadb = session.open()) {
            dadb.shell("input keyevent " + key.code);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "TV'ye ulaşılamadı: " + e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/api/text")
    public ResponseEntity<Map<String, String>> text(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "").replaceAll("\\p{Cntrl}", "").trim();
        if (text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Metin boş olamaz"));
        }
        if (text.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Metin en fazla 200 karakter olabilir"));
        }

        ResponseEntity<Map<String, String>> offline = requireSession();
        if (offline != null) return offline;

        try (Dadb dadb = session.open()) {
            AdbShellResponse response = dadb.shell(inputTextCommand(text));
            if (response.getExitCode() != 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Yazı gönderilemedi: " + response.getAllOutput()));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "TV'ye ulaşılamadı: " + e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private ResponseEntity<Map<String, String>> requireSession() {
        if (!session.isConnected()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Önce TV'ye bağlan"));
        }
        return null;
    }

    private static String inputTextCommand(String text) {
        StringBuilder command = new StringBuilder();
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            boolean end = i == text.length();
            char current = end ? 0 : text.charAt(i);
            if (end || current == '%') {
                if (!chunk.isEmpty()) {
                    appendText(command, chunk.toString());
                    chunk.setLength(0);
                }
                if (!end) {
                    appendText(command, "%");
                }
            } else {
                chunk.append(current);
            }
        }
        return command.toString();
    }

    private static void appendText(StringBuilder command, String chunk) {
        if (!command.isEmpty()) {
            command.append(" && ");
        }
        command.append("input text ").append(shellQuote(chunk));
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
