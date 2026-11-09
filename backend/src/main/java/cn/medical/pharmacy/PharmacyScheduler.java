package cn.medical.pharmacy;
import org.springframework.scheduling.annotation.*;import org.springframework.stereotype.Component;
@Component public class PharmacyScheduler { private final PharmacyService service; public PharmacyScheduler(PharmacyService service){this.service=service;} @Scheduled(fixedDelayString="${medical.pharmacy-scan-delay:5000}") public void scan(){try{service.expireDue();}catch(RuntimeException ignored){}} }
