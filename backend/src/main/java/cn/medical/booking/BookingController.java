package cn.medical.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patient/appointments")
public class BookingController {
    private final BookingService booking;
    public BookingController(BookingService booking) { this.booking = booking; }
    public record CreateInput(@NotBlank @Size(max=36) String scheduleId, @NotBlank String source,
        @NotBlank @Size(max=80) String idempotencyKey) {}
    @PostMapping ResponseEntity<BookingMapper.Appointment> create(JwtAuthenticationToken auth, @Valid @RequestBody CreateInput input) {
        return ResponseEntity.status(201).body(booking.create(auth.getName(), input.scheduleId(), input.source(), input.idempotencyKey()));
    }
    @GetMapping("/{id}") BookingMapper.Appointment own(JwtAuthenticationToken auth, @PathVariable String id) {
        return booking.own(auth.getName(), id);
    }
    @PostMapping("/{id}/simulate-payment") BookingService.PaymentReceipt simulatePayment(JwtAuthenticationToken auth, @PathVariable String id) {
        return booking.simulatePayment(auth.getName(), id);
    }
}
