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
 @Test void stockAdjustmentKeepsHeldQuantityAndStoresAvailableBalance(){
  var mapper=mock(PharmacyMapper.class); var clock=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
  var medicine=new PharmacyMapper.Medicine("m","药","盒",100,8,2,true,1,1);
  when(mapper.lockMedicine("m")).thenReturn(medicine); when(mapper.held("m")).thenReturn(3); when(mapper.history("m")).thenReturn(0); when(mapper.updateMedicine(any())).thenReturn(1);
  var service=new PharmacyService(mapper,mock(BookingService.class),mock(ReceptionService.class),clock,mock(RulesService.class));
  var result=service.adjustStock("m",10);
  assertThat(result.availableStock()).isEqualTo(7); assertThat(result.heldStock()).isEqualTo(3); assertThat(result.totalStock()).isEqualTo(10);
  verify(mapper).updateMedicine(argThat(value -> value.stock()==7));
 }
 @Test void stockAdjustmentRejectsTotalBelowPendingHold(){
  var mapper=mock(PharmacyMapper.class); var clock=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
  when(mapper.lockMedicine("m")).thenReturn(new PharmacyMapper.Medicine("m","药","盒",100,8,2,true,1,1)); when(mapper.held("m")).thenReturn(5);
  var service=new PharmacyService(mapper,mock(BookingService.class),mock(ReceptionService.class),clock,mock(RulesService.class));
  var failure=org.assertj.core.api.Assertions.catchThrowable(() -> service.adjustStock("m",4));
  assertThat(failure).isInstanceOf(cn.medical.identity.ApiFailure.class).hasMessageContaining("锁定库存"); verify(mapper,never()).updateMedicine(any());
 }
}
