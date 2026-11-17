package cn.medical.clinical;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClinicalFileStore {
    private final Path root;
    public ClinicalFileStore(@Value("${medical.attachments-root:data/medical-attachments}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    public void write(String key, byte[] content) throws IOException {
        var path = resolve(key); Files.createDirectories(path.getParent());
        boolean created = false;
        try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            created = true; output.write(content);
        } catch (IOException error) {
            if (created) Files.deleteIfExists(path);
            throw error;
        }
    }
    public byte[] read(String key) throws IOException { return Files.readAllBytes(resolve(key)); }
    public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }
    private Path resolve(String key) {
        var path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid private attachment key");
        return path;
    }
}
