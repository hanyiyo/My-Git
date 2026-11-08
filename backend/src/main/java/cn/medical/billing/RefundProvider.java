package cn.medical.billing;

public interface RefundProvider {
    String refund(String idempotencyKey, String paymentId, long amountMinor) throws Exception;
}
