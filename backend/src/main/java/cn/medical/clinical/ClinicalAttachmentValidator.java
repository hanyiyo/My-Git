package cn.medical.clinical;

import cn.medical.identity.ApiFailure;
import java.util.Locale;
import org.springframework.web.multipart.MultipartFile;

public final class ClinicalAttachmentValidator {
    static final long MAX_BYTES = 10 * 1024 * 1024;
    private ClinicalAttachmentValidator() {}

    public static String validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new ApiFailure(400, "ATTACHMENT_INVALID", "医疗附件不能为空且每份不能超过10MB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        byte[] head;
        try { head = file.getBytes(); }
        catch (Exception error) { throw new ApiFailure(400, "ATTACHMENT_INVALID", "医疗附件无法读取"); }
        boolean jpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        boolean png = lower.endsWith(".png");
        boolean pdf = lower.endsWith(".pdf");
        boolean actualJpeg = head.length >= 3 && (head[0] & 0xff) == 0xff && (head[1] & 0xff) == 0xd8 && (head[2] & 0xff) == 0xff;
        boolean actualPng = head.length >= 8 && (head[0] & 0xff) == 0x89 && head[1] == 0x50 && head[2] == 0x4e && head[3] == 0x47 && head[4] == 0x0d && head[5] == 0x0a && head[6] == 0x1a && head[7] == 0x0a;
        boolean actualPdf = head.length >= 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-';
        if (!((jpeg && actualJpeg) || (png && actualPng) || (pdf && actualPdf)))
            throw new ApiFailure(400, "ATTACHMENT_FORMAT_UNSUPPORTED", "仅支持内容与扩展名一致的JPEG、PNG或PDF附件");
        return jpeg ? "image/jpeg" : png ? "image/png" : "application/pdf";
    }
}
