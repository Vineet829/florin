package com.florin.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.florin.common.ApiException;
import com.florin.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class UploadServiceTest {

    private static final byte[] PNG = TestImages.image("png", 120, 40);
    private static final byte[] JPEG = TestImages.image("jpg", 120, 40);
    private static final byte[] GIF = TestImages.image("gif", 120, 40);
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    private static UploadService serviceFor(Path dir) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("a", Duration.ofMinutes(10), Duration.ofDays(1)),
                new AppProperties.Cookie("jwt", "/", "None", true),
                new AppProperties.Site("Florin", "http://localhost:3000", "http://localhost:8080", "from@florin.local"),
                new AppProperties.Verification(Duration.ofMinutes(15)),
                new AppProperties.Upload(dir.toString()));
        return new UploadService(properties);
    }

    @Test
    void storesImage(@TempDir Path dir) throws Exception {
        UploadService service = serviceFor(dir);
        MockMultipartFile logo = new MockMultipartFile("logo", "brand.png", "image/png", PNG);

        String url = service.store(logo);

        assertThat(url).startsWith("/uploads/").endsWith(".png");
        String fileName = url.substring("/uploads/".length());
        assertThat(Files.exists(dir.resolve(fileName))).isTrue();
    }

    @Test
    void rejectsEmptyFile(@TempDir Path dir) {
        UploadService service = serviceFor(dir);
        MockMultipartFile empty = new MockMultipartFile("logo", "e.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.store(empty))
                .isInstanceOf(ApiException.class)
                .hasMessage("A logo file is required");
    }

    @Test
    void rejectsNonImage(@TempDir Path dir) {
        UploadService service = serviceFor(dir);
        MockMultipartFile text =
                new MockMultipartFile("logo", "note.txt", "text/plain", new byte[] {9, 9});

        assertThatThrownBy(() -> service.store(text))
                .isInstanceOf(ApiException.class)
                .hasMessage("Logos can be PNG, JPEG or GIF images")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refusesDisguisedPages(@TempDir Path dir) {
        UploadService service = serviceFor(dir);
        byte[] page = "<html><p>Not an image</p></html>".getBytes();

        for (String name : new String[] {"page.html", "page.png", "page.svg"}) {
            assertThatThrownBy(() -> service.store(new MockMultipartFile("logo", name, "image/png", page)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("Logos can be PNG, JPEG or GIF images");
        }
        assertThat(dir.toFile().listFiles()).isEmpty();
    }

    @Test
    void namesByContent(@TempDir Path dir) {
        UploadService service = serviceFor(dir);

        assertThat(service.store(new MockMultipartFile("logo", "page.html", "text/html", PNG))).endsWith(".png");
        assertThat(service.store(new MockMultipartFile("logo", "photo", "image/png", JPEG))).endsWith(".jpg");
        assertThat(service.store(new MockMultipartFile("logo", "anim.png", "image/gif", GIF))).endsWith(".gif");
        assertThatThrownBy(() -> service.store(new MockMultipartFile("logo", "logo.webp", "image/webp", WEBP)))
                .hasMessage("Logos can be PNG, JPEG or GIF images");
    }

    @Test
    void resolvesStoredFile(@TempDir Path dir) throws Exception {
        UploadService service = serviceFor(dir);
        String url = service.store(new MockMultipartFile("logo", "brand.png", "image/png", PNG));

        assertThat(service.localFile(url)).contains(dir.resolve(url.substring("/uploads/".length())).toAbsolutePath());
    }

    @Test
    void refusesOtherPaths(@TempDir Path dir) throws Exception {
        UploadService service = serviceFor(dir.resolve("uploads"));
        Files.writeString(dir.resolve("secret.txt"), "secret");

        assertThat(service.localFile("https://example.com/logo.png")).isEmpty();
        assertThat(service.localFile("/uploads/missing.png")).isEmpty();
        assertThat(service.localFile("/uploads/../secret.txt")).isEmpty();
        assertThat(service.localFile(null)).isEmpty();
    }

    @Test
    void refusesLargeImages(@TempDir Path dir) {
        UploadService service = serviceFor(dir);

        for (byte[] large : new byte[][] {TestImages.pngHeader(12000, 12000), TestImages.pngHeader(2001, 2000)}) {
            assertThatThrownBy(() -> service.store(new MockMultipartFile("logo", "big.png", "image/png", large)))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("Logos can be at most 4 megapixels, such as 2000 × 2000 pixels")
                    .extracting(e -> ((ApiException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(dir.toFile().listFiles()).isEmpty();

        byte[] wide = TestImages.pngHeader(4000, 1000);
        assertThat(service.store(new MockMultipartFile("logo", "wide.png", "image/png", wide))).endsWith(".png");
    }

    @Test
    void refusesUnreadableImages(@TempDir Path dir) {
        UploadService service = serviceFor(dir);
        byte[] cutShort = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

        assertThatThrownBy(() -> service.store(new MockMultipartFile("logo", "logo.png", "image/png", cutShort)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Logos can be PNG, JPEG or GIF images");
        assertThat(dir.toFile().listFiles()).isEmpty();
    }

    @Test
    void printsReadableSmallLogos(@TempDir Path dir) throws Exception {
        assertThat(UploadService.printable(Files.write(dir.resolve("small.png"), PNG))).isTrue();
        assertThat(UploadService.printable(Files.write(dir.resolve("edge.png"), TestImages.pngHeader(2000, 2000))))
                .isTrue();
        assertThat(UploadService.printable(Files.write(dir.resolve("huge.png"), TestImages.pngHeader(12000, 12000))))
                .isFalse();
        assertThat(UploadService.printable(Files.write(dir.resolve("text.png"), new byte[] {9, 9}))).isFalse();
        assertThat(UploadService.printable(dir.resolve("missing.png"))).isFalse();
    }

    @Test
    void refusesLargeFiles(@TempDir Path dir) {
        UploadService service = serviceFor(dir);
        MockMultipartFile large = new MockMultipartFile(
                "logo", "big.png", "image/png", new byte[(int) UploadService.MAX_BYTES + 1]);

        assertThatThrownBy(() -> service.store(large))
                .isInstanceOf(ApiException.class)
                .hasMessage("Logos can be at most 2 MB")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(dir.toFile().listFiles()).isEmpty();
    }
}
