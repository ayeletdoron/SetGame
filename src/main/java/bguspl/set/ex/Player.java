package bguspl.set.ex;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import bguspl.set.Env;


/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    protected enum DealerRespond {
        POINT, PENALTY, DISGRACE, TIMEOVER
    }

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    protected Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    protected LinkedBlockingQueue<Integer> actions;

    private Dealer dealer;

    //If you should empty the queue of actions
    private boolean shouldEmptyQueue;

    private LinkedBlockingQueue<DealerRespond> answerFromDealer;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.shouldEmptyQueue = false;
        this.actions = new LinkedBlockingQueue<>(env.config.featureSize);
        this.answerFromDealer = new LinkedBlockingQueue<>(1);
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate && !Thread.currentThread().isInterrupted()) {
            // TODO implement main player loop
            if (shouldEmptyQueue) {
                emptyActionsQueue();
                shouldEmptyQueue = false;
            }
            try {
                int currentAction = actions.take();
                boolean hasRemoved = table.removeToken(id, currentAction);
                if (!hasRemoved && table.getNumOfTokens(id) < env.config.featureSize) {
                    table.placeToken(id, currentAction);
                    try {
                        if (table.getNumOfTokens(id) == env.config.featureSize) {
                            dealer.testMySet(id);
                            DealerRespond dealerRespond = answerFromDealer.take();
                            switch (dealerRespond) {
                                case POINT: {
                                    point();
                                    break;
                                }
                                case PENALTY: {
                                    penalty();
                                    break;
                                }
                                case DISGRACE: {
                                    break;
                                }
                                case TIMEOVER: {
                                    break;
                                }
                            }
                            shouldEmptyQueue = true;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!human) try {
            aiThread.interrupt();
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate && !Thread.currentThread().isInterrupted()) {
                // TODO implement player key press simulator
                int randomSlot = (int) (Math.random() * env.config.tableSize);
                try {
                    actions.put((Integer) randomSlot);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
    }


    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        try {
            actions.put((Integer) slot);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        try {
            long freezeTime = System.currentTimeMillis() + env.config.pointFreezeMillis;
            while (freezeTime - System.currentTimeMillis() >= 1000){
                env.ui.setFreeze(id, freezeTime - System.currentTimeMillis());
                Thread.sleep(900);}
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        env.ui.setFreeze(id, 0);
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        try {
            long freezeTime = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
            while (freezeTime - System.currentTimeMillis() >= 1000) {
                env.ui.setFreeze(id, freezeTime - System.currentTimeMillis());
                Thread.sleep(900);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        env.ui.setFreeze(id, 0);
    }

    public int getScore() {
        return score;
    }

    public void emptyActionsQueue() {
        actions.clear();
    }

    public void returnAnswer(DealerRespond answer) {
        try {
            answerFromDealer.put(answer);
        } catch (InterruptedException e) {
        }
    }
}
