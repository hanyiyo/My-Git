package cn.medical.clinical;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ClinicalFileStoreTest {
    @TempDir Path root;
    @Test void privateStorePreservesExistingFilesAndRejectsEscapingKeys() throws Exception {
        var store = new ClinicalFileStore(root.toString()); byte[] original = {1, 2, 3};
        store.write("appointment/attachment.bin", original);
        assertThat(store.read("appointment/attachment.bin")).isEqualTo(original);
        assertThatThrownBy(() -> store.write("appointment/attachment.bin", new byte[]{4})).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(store.read("appointment/attachment.bin")).isEqualTo(original);
        assertThatThrownBy(() -> store.write("../outside.bin", original)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.read("../outside.bin")).isInstanceOf(IllegalArgumentException.class);
        store.delete("appointment/attachment.bin");
        assertThat(root.resolve("appointment/attachment.bin")).doesNotExist();
    }
}
