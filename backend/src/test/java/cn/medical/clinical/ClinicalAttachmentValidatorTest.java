package cn.medical.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ClinicalAttachmentValidatorTest {
    @Test void acceptsContentMatchingAllowedExtensions() {
        assertThat(ClinicalAttachmentValidator.validate(new MockMultipartFile("files", "report.pdf", "application/pdf", "%PDF-1.7".getBytes()))).isEqualTo("application/pdf");
        assertThat(ClinicalAttachmentValidator.validate(new MockMultipartFile("files", "photo.png", "image/png", new byte[]{(byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))).isEqualTo("image/png");
    }
    @Test void rejectsExtensionContentMismatch() {
        assertThatThrownBy(() -> ClinicalAttachmentValidator.validate(new MockMultipartFile("files", "report.pdf", "application/pdf", "not-a-pdf".getBytes())))
            .isInstanceOf(cn.medical.identity.ApiFailure.class).hasMessage("仅支持内容与扩展名一致的JPEG、PNG或PDF附件");
    }
    @Test void rejectsFilesOverTenMegabytes() {
        assertThatThrownBy(() -> ClinicalAttachmentValidator.validate(new MockMultipartFile("files", "report.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])))
            .isInstanceOf(cn.medical.identity.ApiFailure.class).hasMessage("医疗附件不能为空且每份不能超过10MB");
    }
}
