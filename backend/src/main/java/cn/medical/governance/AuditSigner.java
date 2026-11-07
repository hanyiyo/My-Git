package cn.medical.governance;

import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuditSigner {
    public record Checkpoint(long sequence, String signature) {}
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final Path checkpointFile;
    private Checkpoint checkpoint;
    public AuditSigner(@Value("${medical.audit-directory:data/audit}") String directory) {
        try {
            Path root = Path.of(directory).toAbsolutePath().normalize(); Files.createDirectories(root);
            Path privateFile = root.resolve("private.pk8"), publicFile = root.resolve("public.x509");
            if (!Files.exists(privateFile) && !Files.exists(publicFile)) {
                var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                Files.write(privateFile,pair.getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW);
                Files.write(publicFile,pair.getPublic().getEncoded(),StandardOpenOption.CREATE_NEW);
                if (Files.getFileStore(privateFile).supportsFileAttributeView("posix"))
                    Files.setPosixFilePermissions(privateFile,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            }
            var factory = KeyFactory.getInstance("Ed25519");
            privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privateFile)));
            publicKey = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicFile)));
            byte[] probe = "medical-audit-key-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (!verify(probe,sign(probe))) throw new IllegalStateException("Audit keys do not match");
            checkpointFile = root.resolve("checkpoint.txt");
            if (Files.exists(checkpointFile)) {
                var parts = Files.readString(checkpointFile).trim().split(":",2);
                checkpoint = new Checkpoint(Long.parseLong(parts[0]),parts[1]);
            } else checkpoint = new Checkpoint(0,"0".repeat(128));
        } catch (Exception failure) { throw new IllegalStateException("Cannot load audit signing keys/checkpoint; restore the persistent audit directory",failure); }
    }
    public String publicKey() { return Base64.getEncoder().encodeToString(publicKey.getEncoded()); }
    public String sign(byte[] body) {
        try { var signer = Signature.getInstance("Ed25519"); signer.initSign(privateKey); signer.update(body); return HexFormat.of().formatHex(signer.sign()); }
        catch (GeneralSecurityException failure) { throw new IllegalStateException("Audit signing failed",failure); }
    }
    public boolean verify(byte[] body, String signature) {
        try { var verifier = Signature.getInstance("Ed25519"); verifier.initVerify(publicKey); verifier.update(body); return verifier.verify(HexFormat.of().parseHex(signature)); }
        catch (GeneralSecurityException | IllegalArgumentException failure) { return false; }
    }
    public synchronized Checkpoint checkpoint() {
        try {
            if (Files.exists(checkpointFile)) { var parts = Files.readString(checkpointFile).trim().split(":",2); checkpoint = new Checkpoint(Long.parseLong(parts[0]),parts[1]); }
            return checkpoint;
        } catch (Exception failure) { throw new IllegalStateException("Audit checkpoint cannot be read",failure); }
    }
    public synchronized void checkpoint(long sequence, String signature) {
        var current = checkpoint();
        if (sequence == current.sequence() && !signature.equals(current.signature())) throw new IllegalStateException("Audit checkpoint fork detected");
        if (sequence <= current.sequence()) return;
        try {
            Path pending = checkpointFile.resolveSibling("checkpoint.pending");
            Files.writeString(pending,sequence+":"+signature);
            Files.move(pending,checkpointFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            checkpoint = new Checkpoint(sequence,signature);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Audit checkpoint cannot be persisted",failure); }
    }
}
