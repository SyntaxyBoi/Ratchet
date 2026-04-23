package moth.ratchet;

public final class RebounderVisualState {
    private static volatile long clientWorldTime = -1L;

    private RebounderVisualState() {}

    public static void setClientWorldTime(long time) {
        clientWorldTime = time;
    }

    public static void clearClientWorldTime() {
        clientWorldTime = -1L;
    }

    public static long getClientWorldTime() {
        return clientWorldTime;
    }
}
