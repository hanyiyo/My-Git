package cn.medical.billing;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/billing/refunds")
public class BillingController {
    private final RefundProcessor refunds;
    public BillingController(RefundProcessor refunds) { this.refunds = refunds; }
    @PostMapping("/{id}/process") BillingService.Refund process(@PathVariable String id) { return refunds.process(id); }
}
