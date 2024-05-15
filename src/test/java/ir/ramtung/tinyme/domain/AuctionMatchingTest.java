package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class AuctionMatchingTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setup() {
        security = Security.builder().build();
        broker = Broker.builder().credit(100_000_000L).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, BUY, 300, 15700, broker, shareholder,0),
                new Order(2, security, BUY, 40, 15500, broker, shareholder,0),
                new Order(3, security, BUY, 400, 15450, broker, shareholder,0),
                new Order(4, security, BUY, 500, 15450, broker, shareholder,0),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder,0),
                new Order(6, security, Side.SELL, 330, 15800, broker, shareholder,0),
                new Order(7, security, Side.SELL, 300, 15810, broker, shareholder,0),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder,0),
                new Order(9, security, Side.SELL, 200, 15820, broker, shareholder,0),
                new Order(10, security, Side.SELL, 50, 15820, broker, shareholder,0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        System.out.println(orderBook);
    }
    
    @Test
    void find_auction_price_successfully_done(){
        System.out.println("HI");

        
    }
}
