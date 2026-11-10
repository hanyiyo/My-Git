package cn.medical.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/patient/appointments")
public class BookingController {
    private final BookingService booking;
    private final java.time.Clock clock;
    public BookingController(BookingService booking, java.time.Clock clock) { this.booking = booking; this.clock = clock; }
    public record RegistrationHold(@com.fasterxml.jackson.annotation.JsonUnwrapped BookingMapper.Appointment appointment, long serverNow) {}
    public record CreateInput(@NotBlank @Size(max=36) String scheduleId, @NotBlank String source,
        @NotBlank @Size(max=80) String idempotencyKey) {}
    @GetMapping List<BookingService.OrderView> orders(JwtAuthenticationToken auth) { return booking.orders(auth.getName()); }
    @PostMapping ResponseEntity<RegistrationHold> create(JwtAuthenticationToken auth, @Valid @RequestBody CreateInput input) {
        var appointment = booking.create(auth.getName(), input.scheduleId(), input.source(), input.idempotencyKey());
        return ResponseEntity.status(201).body(new RegistrationHold(appointment, clock.millis()));
    }
    @GetMapping("/{id}") BookingMapper.Appointment own(JwtAuthenticationToken auth, @PathVariable String id) {
        return booking.own(auth.getName(), id);
    }
    @PostMapping("/{id}/simulate-payment") BookingService.PaymentReceipt simulatePayment(JwtAuthenticationToken auth, @PathVariable String id) {
        return booking.simulatePayment(auth.getName(), id);
    }
    @PostMapping("/{id}/cancel") BookingMapper.Appointment cancel(JwtAuthenticationToken auth, @PathVariable String id) {
        return booking.cancel(auth.getName(), id);
    }
    @GetMapping("/{id}/billing") cn.medical.billing.BillingService.FinancialStatus billing(JwtAuthenticationToken auth, @PathVariable String id) {
        return booking.financialStatus(auth.getName(), id);
    }
}
