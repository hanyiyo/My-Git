package cn.medical.reception;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReceptionScheduler {
    private final ReceptionService reception;
    public ReceptionScheduler(ReceptionService reception) { this.reception = reception; }
    @Scheduled(fixedDelayString="${medical.reception-scan-delay:5000}", initialDelayString="${medical.reception-scan-delay:5000}")
    public void markNoShows() { reception.markNoShows(); }
}
