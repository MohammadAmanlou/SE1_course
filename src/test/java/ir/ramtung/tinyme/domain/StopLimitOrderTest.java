package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
class StopLimitOrderTest {

    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().credit(100_000_000L).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder,0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder,0),
                new Order(3, security, BUY, 445, 15450, broker, shareholder,0),
                new Order(4, security, BUY, 526, 15450, broker, shareholder,0),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder,0),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder,0),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder,0),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder,0),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder,0),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder,0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }


    /*@Test
    void new_sell_order_matches_completely_with_part_of_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 100, 15600, broker, shareholder,0);
        Trade trade = new Trade(security, 15700, 100, orders.get(0), order);
        MatchResult result = matcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(204);

        orders = Arrays.asList(
                new IcebergOrder(1, security, BUY, 450, 15450, broker, shareholder, 200,0),
                new Order(2, security, BUY, 70, 15450, broker, shareholder,0),
                new Order(3, security, BUY, 1000, 15400, broker, shareholder,0)
        );
    }*/
    @Test  //slo =stop limit order 1
    void slo_get_activated_because_buy_trade_is_more_or_equal_to_stop_price() {      
       Order order = new Order(11, security, Side.BUY, 100, 15800, broker, shareholder,0);
       StopLimitOrder stoplimitorder  =new StopLimitOrder(12, security, Side.BUY, 50, 15000, broker, shareholder, LocalDateTime.now(), 15000) ;    
        MatchResult result1 = matcher.match(order);
        MatchResult result2 = matcher.match(stoplimitorder);
        assertThat(result1.remainder().getQuantity()).isEqualTo(0);
        assertThat(result2.remainder().getQuantity()).isEqualTo(50);
        //check this part
        assertThat(stoplimitorder.getIsActive()).isEqualTo(true);
    }
    @Test  //2
    void slo_get_activated_because_sell_trade_is_less_or_equal_to_stop_price() {
      // new_sell_order_matches_completely_with_part_of_the_first_buy
      Order order = new Order(11, security, Side.SELL, 100, 15600, broker, shareholder,0);
      StopLimitOrder stoplimitorder  =new StopLimitOrder(12, security, Side.SELL, 50, 15600, broker, shareholder, LocalDateTime.now(), 15600) ;    
       MatchResult result1 = matcher.execute(order);   
       MatchResult result2 = matcher.execute(stoplimitorder);
       assertThat(result1.remainder().getQuantity()).isEqualTo(0);
       assertThat(result2.remainder().getQuantity()).isEqualTo(0);
       //check this part
       assertThat(stoplimitorder.getIsActive()).isEqualTo(true);
    }

    @Test  //3
    void slo_not_activated_because_buy_trade_is_less_than_stop_price() { 
        Order order = new Order(11, security, Side.BUY, 100, 15800, broker, shareholder,0);
        StopLimitOrder stoplimitorder  =new StopLimitOrder(12, security, Side.BUY, 50, 15900, broker, shareholder, LocalDateTime.now(), 15900) ;    
        MatchResult result1 = matcher.match(order);
        MatchResult result2 = matcher.match(order);
        assertThat(result1.remainder().getQuantity()).isEqualTo(0);
        assertThat(result2.remainder().getQuantity()).isEqualTo(0);
        //check this part
        assertThat(stoplimitorder.getIsActive()).isEqualTo(false);
    }

    @Test  //4
    void slo_not_activated_because_sell_trade_is_more_than_stop_price() { 
        Order order = new Order(11, security, Side.BUY, 100, 15800, broker, shareholder,0);
        StopLimitOrder stoplimitorder  =new StopLimitOrder(12, security, Side.BUY, 50, 15900, broker, shareholder, LocalDateTime.now(), 15900) ;    
        MatchResult result1 = matcher.match(order);
        MatchResult result2 = matcher.match(order);
        assertThat(result1.remainder().getQuantity()).isEqualTo(0);
        assertThat(result2.remainder().getQuantity()).isEqualTo(0);
        //check this part
        assertThat(stoplimitorder.getIsActive()).isEqualTo(false);
    }

    

 
}
