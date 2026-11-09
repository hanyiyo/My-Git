package cn.medical.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import cn.medical.booking.BookingService;
import cn.medical.reception.ReceptionService;
import cn.medical.settings.RulesService;

class PharmacyServiceTest {
 @Test void adminCannotLowerStockBelowPendingHold(){
  var mapper=mock(PharmacyMapper.class); var clock=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
  when(mapper.lockMedicine("m")).thenReturn(new PharmacyMapper.Medicine("m","药","盒",100,8,2,true,1,1)); when(mapper.held("m")).thenReturn(5);
  var service=new PharmacyService(mapper,mock(BookingService.class),mock(ReceptionService.class),clock,mock(RulesService.class));
  var failure=org.assertj.core.api.Assertions.catchThrowable(() -> service.saveMedicine("m","药","盒",100,4,2,true));
  assertThat(failure).isInstanceOf(cn.medical.identity.ApiFailure.class).hasMessageContaining("锁定库存"); verify(mapper,never()).updateMedicine(any());
 }
}
