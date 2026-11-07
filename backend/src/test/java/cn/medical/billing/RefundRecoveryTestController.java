package cn.medical.billing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@TestConfiguration
public class RefundRecoveryTestController {
    @RestController
    @RequestMapping("/api/v1/admin/test/refunds")
    static class RecoveryController {
        private final RefundProcessor refunds;
        RecoveryController(RefundProcessor refunds) { this.refunds = refunds; }
        @PostMapping("/{id}/crash") ResponseEntity<BillingService.Refund> crash(@PathVariable String id) {
            return ResponseEntity.status(202).body(refunds.process(id, true));
        }
    }
}
