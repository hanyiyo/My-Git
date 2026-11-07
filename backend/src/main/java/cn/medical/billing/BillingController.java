package cn.medical.billing;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/admin/billing/refunds")
public class BillingController {
    private final RefundProcessor refunds;
    public BillingController(RefundProcessor refunds) { this.refunds = refunds; }
    @PostMapping("/{id}/process")
    ResponseEntity<BillingService.Refund> process(@PathVariable String id, @RequestParam(defaultValue="false") boolean crashAfterClaim) {
        var refund = refunds.process(id, crashAfterClaim);
        return ResponseEntity.status(crashAfterClaim ? 202 : 200).body(refund);
    }
}
