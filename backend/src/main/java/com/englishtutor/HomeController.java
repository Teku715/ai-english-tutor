package com.englishtutor;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class HomeController {

    @GetMapping(value = {"/", "/index.html"})
    public ResponseEntity<String> index() {
        try {
            return htmlResponse("static/index.html");
        } catch (IOException e) {
            String fallback = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>AI 英语口语陪练</title></head>"
                    + "<body style='font-family:sans-serif;padding:24px;'>"
                    + "<h1>AI 英语口语陪练</h1>"
                    + "<p>前端文件未找到，但后端已启动。</p>"
                    + "<p>请重新运行桌面上的「启动英语口语陪练.bat」。</p>"
                    + "<p><a href='/api/health'>测试 /api/health</a></p>"
                    + "</body></html>";
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(fallback);
        }
    }

    @GetMapping(value = "/app.js", produces = "application/javascript")
    public ResponseEntity<String> appJs() {
        try {
            return textResponse("static/app.js", MediaType.valueOf("application/javascript"));
        } catch (IOException e) {
            return ResponseEntity.status(404).body("console.error('app.js missing');");
        }
    }

    @GetMapping(value = "/styles.css", produces = "text/css")
    public ResponseEntity<String> styles() {
        try {
            return textResponse("static/styles.css", MediaType.valueOf("text/css"));
        } catch (IOException e) {
            return ResponseEntity.ok().contentType(MediaType.valueOf("text/css")).body("body{font-family:sans-serif;padding:20px;}");
        }
    }

    private ResponseEntity<String> htmlResponse(String classpathLocation) throws IOException {
        return textResponse(classpathLocation, MediaType.TEXT_HTML);
    }

    private ResponseEntity<String> textResponse(String classpathLocation, MediaType mediaType) throws IOException {
        byte[] bytes = readResourceBytes(classpathLocation);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        return ResponseEntity.ok().headers(headers).body(new String(bytes, StandardCharsets.UTF_8));
    }

    private byte[] readResourceBytes(String classpathLocation) throws IOException {
        Resource resource = new ClassPathResource(classpathLocation);
        if (resource.exists()) {
            return StreamUtils.copyToByteArray(resource.getInputStream());
        }

        Path filePath = resolveDevPath(classpathLocation);
        if (Files.isRegularFile(filePath)) {
            return Files.readAllBytes(filePath);
        }

        throw new IOException("Missing frontend asset: " + classpathLocation);
    }

    private Path resolveDevPath(String classpathLocation) {
        String relative = classpathLocation.replace("static/", "");
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path fromBackend = cwd.resolve("src").resolve("main").resolve("resources").resolve(classpathLocation);
        if (Files.isRegularFile(fromBackend)) {
            return fromBackend;
        }
        Path fromRoot = cwd.resolve("backend").resolve("src").resolve("main").resolve("resources").resolve(classpathLocation);
        if (Files.isRegularFile(fromRoot)) {
            return fromRoot;
        }
        return cwd.resolve("src").resolve("main").resolve("resources").resolve("static").resolve(relative);
    }
}
