package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class DealerTest {
    private Env env;
    private Table table;
    private Player[] players;
    private Dealer dealer;

    @BeforeEach
    void setUp() {

        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        TableTest.MockLogger logger = new TableTest.MockLogger();
        Config config = new Config(logger, properties);


        Env env = new Env(logger, config, new TableTest.MockUserInterface(), new TableTest.MockUtil());
        Integer[] slotToCard = new Integer[config.tableSize];
        Integer[] cardToSlot = new Integer[config.deckSize];
        table = new Table(env, slotToCard, cardToSlot);
        players = new Player[config.players];
        dealer = new Dealer(env,table,players);
        for (int i = 0; i < config.players; i++) {
            players[i] = new Player(env,dealer,table,i,i < env.config.humanPlayers);
        }


    }
    private void fillAllSlots() {
        for (int i = 0; i < table.slotToCard.length; ++i) {
            table.slotToCard[i] = i;
            table.cardToSlot[i] = i;
        }
    }
    @Test
    void sizePlusOneTestWhenQueueIsNotFull(){
        int expectedSize = dealer.playersSetsOrder.size() + 1;
        dealer.testMySet(0);
        assertEquals(expectedSize, dealer.playersSetsOrder.size());
    }
    @Test
    void dealer_removeAllCardsFromTable(){
        fillAllSlots();
        dealer.removeAllCardsFromTable();
        assertEquals(0,table.countCards());
    }
}
