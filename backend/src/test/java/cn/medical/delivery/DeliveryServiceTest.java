package cn.medical.delivery;

import static org.mockito.Mockito.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class DeliveryServiceTest {
    @Test void publishPersistsOnlyAndNeverCallsTransportInsideBusinessTransaction() {
        var mapper=mock(DeliveryMapper.class);var transport=mock(SseTransport.class);
        var service=new DeliveryService(mapper,mock(RuntimeTaskMapper.class),transport,mock(cn.medical.identity.IdentityService.class),Clock.systemUTC(),mock(org.springframework.transaction.PlatformTransactionManager.class));
        service.publish("patient","APPOINTMENT_CONFIRMED","1","已确认","appointment-confirmed:1");
        verify(mapper).insert(any());verifyNoInteractions(transport);verify(mapper,never()).lockCursor();
    }
}
