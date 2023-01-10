package bguspl.set.ex;

import bguspl.set.Env;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    //queue of waiting players for their set test
    protected LinkedBlockingQueue<Integer> playersSetsOrder;

    //slots of the current legal set
    private int[] currentSetSlots;

    private int timeToSleep;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playersSetsOrder = new LinkedBlockingQueue<>(players.length);
        this.currentSetSlots = null;
        timeToSleep = 1000;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        //creates players threads
        for (int i = 0; i < players.length; i++) {
            Thread player = new Thread(players[i]);
            player.start();
        }
        //main interval - every 60 seconds
        while (!shouldFinish()) {
            table.lock.dealerLock();
            placeCardsOnTable();
            for (int i = 0; i < players.length; i++) {
                players[i].emptyActionsQueue();
                if (playersSetsOrder.contains(i)) {
                    players[i].returnAnswer(Player.DealerRespond.TIMEOVER);
                }
            }
            playersSetsOrder.clear();
            updateTimerDisplay(true);
            table.lock.dealerUnlock();
            timerLoop();
            table.lock.dealerLock();
            removeAllCardsFromTable();
            table.lock.dealerUnlock();
        }
        //interrupt all players threads
        for (int i = 0; i < players.length; i++) {
            players[i].playerThread.interrupt();
            try {
                players[i].playerThread.join();
            } catch (InterruptedException ignored) {
            }
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            table.lock.dealerLock();
            removePlayersIfNeeded();
            removeCardsFromTable();
            placeCardsOnTable();
            table.lock.dealerUnlock();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        for (int i = 0; i < players.length; i++) {
            players[i].terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    public void testMySet(int playerID) {
        playersSetsOrder.add(playerID);
    }


    private void removeCardsFromTable() {
        // TODO implement
        if (currentSetSlots != null) {
            for (int i = 0; i < currentSetSlots.length; i++) {
                table.removeCard(currentSetSlots[i]);
            }
        }
        currentSetSlots = null;
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] == null) {
                if (deck.size() != 0) {
                    int currentCard = (int) (Math.random() * deck.size());
                    table.placeCard(deck.get(currentCard), i);
                    deck.remove(currentCard);
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            Integer currPlayer = playersSetsOrder.poll(timeToSleep, TimeUnit.MILLISECONDS);
            if (currPlayer != null) {
                boolean isLegal = isLegalSet(currPlayer);
                if (isLegal) {
                    players[currPlayer].returnAnswer(Player.DealerRespond.POINT);
                    updateTimerDisplay(true);
                } else {
                    currentSetSlots = null;
                    players[currPlayer].returnAnswer(Player.DealerRespond.PENALTY);
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    private void removePlayersIfNeeded() {
        //remove waiting for check players that have tokens on the same slot
        if (currentSetSlots != null) {
            for (int i = 0; i < currentSetSlots.length; i++) {
                for (int j = 0; j < env.config.players; j++) {
                    if (table.tokensPerPlayer[currentSetSlots[i]][j]) {
                        boolean hasRemovedFromOrder = playersSetsOrder.remove(j);
                        if (hasRemovedFromOrder) {
                            players[j].returnAnswer(Player.DealerRespond.DISGRACE);
                        }
                    }
                }
            }
        }
    }

    private boolean isLegalSet(int playerId) {
        int[] cards = new int[env.config.featureSize];
        int j = 0;
        currentSetSlots = new int[env.config.featureSize];
        for (int i = 0; i < table.tokensPerPlayer.length; i++) {
            if (table.tokensPerPlayer[i][playerId]) {
                cards[j] = table.slotToCard[i];
                currentSetSlots[j] = i;
                j++;
            }
        }
        return env.util.testSet(cards);
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset) {
            timeToSleep = 1000;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        } else if (reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis) {
            timeToSleep = 10;
            env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0), true);
        } else {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    protected void removeAllCardsFromTable() {
        // TODO implement
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null) {
                deck.add((Integer) table.slotToCard[i]);
                table.removeCard(i);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        if (!terminate) {
            int maxScore = 0;
            int counter = 0;
            for (int i = 0; i < players.length; i++) {
                if (players[i].getScore() > maxScore) {
                    maxScore = players[i].getScore();
                    counter = 1;
                } else if (players[i].getScore() == maxScore) {
                    counter++;
                }
            }

            int[] winners = new int[counter];
            int j = 0;
            for (int i = 0; i < players.length; i++) {
                if (players[i].getScore() == maxScore) {
                    winners[j] = i;
                    j++;
                }
            }

            env.ui.announceWinner(winners);
        }
    }
}

