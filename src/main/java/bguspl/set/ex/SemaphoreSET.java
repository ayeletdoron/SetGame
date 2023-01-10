package bguspl.set.ex;

public class SemaphoreSET {
    int activePlayers = 0, activeDealer = 0, waitingDealer = 0;

    public SemaphoreSET() {
    }

    public synchronized void playerLock() {
        try {
            while (!allowPlayer()) {
                wait();
            }
        } catch (InterruptedException ignored) {
        }
        activePlayers++;
    }

    public synchronized void playerUnlock() {
        activePlayers--;
        notifyAll();
    }

    public synchronized void dealerLock() {
        waitingDealer++;
        try {
            while (!allowDealer()) {
                wait();
            }
        } catch (InterruptedException ignored) {
        }
        waitingDealer--;
        activeDealer++;
    }

    public synchronized void dealerUnlock() {
        activeDealer--;
        notifyAll();
    }

    protected boolean allowPlayer() {
        return activeDealer == 0 && waitingDealer == 0;
    }

    protected boolean allowDealer() {
        return activePlayers == 0;
    }
}
