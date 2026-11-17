package cn.medical.reception;

/** Read-only seam used to decide whether an online call was answered. */
@FunctionalInterface
public interface ReceptionResponseObserver {
    boolean hasPatientReplyAfter(String appointmentId, long calledAt);
}
