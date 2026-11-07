package cn.medical;

import java.time.Instant;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.*;

// Imported only by browser acceptance; never packaged into the application.
@TestConfiguration(proxyBeanMethods = false)
@RestController
class BrowserClockControl {
    private final BookingHttpTest.AdjustableClock clock;
    BrowserClockControl(BookingHttpTest.AdjustableClock clock) { this.clock = clock; }
    @PostMapping("/api/v1/admin/test-clock")
    void set(@RequestBody Map<String, Long> input) {
        clock.now = input.containsKey("millis") ? Instant.ofEpochMilli(input.get("millis")) : Instant.now();
    }
}
