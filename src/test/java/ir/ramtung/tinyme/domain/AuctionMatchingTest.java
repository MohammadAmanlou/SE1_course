package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.OpeningPriceEvent;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.request.ChangeMatchStateRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

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
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;

    @BeforeEach
    void setup() {
        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);
        broker = Broker.builder().credit(100_000_000L).build();
        brokerRepository.addBroker(broker);
        shareholder = Shareholder.builder().build();
        shareholderRepository.addShareholder(shareholder);
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder,0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder,0),
                new Order(3, security, BUY, 445, 15450, broker, shareholder,0),
                new Order(4, security, BUY, 526, 15450, broker, shareholder,0),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder,0),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder,0),
                new Order(7, security, Side.SELL, 285, 15490, broker, shareholder,0),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder,0),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder,0),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder,0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        System.out.println(orderBook);
    }
    
    @Test
    void find_auction_price_successfully_done(){ //checked
       int openingPrice = security.updateIndicativeOpeningPrice();
       assertThat(openingPrice).isEqualTo(15490);
    }

    @Test
    void find_auction_price_successfully_done_when_some_orders_get_removed() { //checked
        orderBook.removeByOrderId(Side.SELL, 10);
        orderBook.removeByOrderId(Side.SELL, 9);
        int openingPrice = security.updateIndicativeOpeningPrice();
        assertThat(openingPrice).isEqualTo(15490);
    }

    @Test
    void default_match_state_is_continuous() {   //checked
        assertThat(security.getMatchingState()).isEqualTo(MatchingState.CONTINUOUS);
    }

    @Test
    void change_match_state_from_continuous_to_auction() { //checked
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.AUCTION));
        assertThat(security.getMatchingState()).isEqualTo(MatchingState.AUCTION);
        verify(eventPublisher).publish(new SecurityStateChangedEvent(LocalDateTime.now() , security.getIsin() , MatchingState.AUCTION));
    }

    

    
//shzd:

   @Test
    void do_auction_process_in_continuous_match_state_succesfully_fails() { //condition bezarim!!! not checked //qalateee
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.CONTINUOUS));
        int openingPrice = security.updateIndicativeOpeningPrice();
        assertThat(openingPrice).isEqualTo(15490);
        assertThat(security.getMatchingState()).isEqualTo(MatchingState.CONTINUOUS);
    }

    @Test
    void no_trade_happens_in_auction_matching_state() { 
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.AUCTION));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 400, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 )); 
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 0));
        int openingPrice = security.updateIndicativeOpeningPrice();
        assertThat(broker.getCredit()).isEqualTo(100_000_000L);  
    }

    @Test
    void find_auction_price_successfully_done_with_not_enough_credit() { 
        broker.decreaseCreditBy(98_000_000);
        int openingPrice = security.updateIndicativeOpeningPrice();
        assertThat(openingPrice).isEqualTo(15490);
    }

    @Test
    void change_match_state_from_auction_to_auction() { //not checked bazgoshayi
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.AUCTION));
        assertThat(security.getMatchingState()).isEqualTo(MatchingState.AUCTION);
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.AUCTION));
        int openingPrice = security.updateIndicativeOpeningPrice();

        assertThat(broker.getCredit()).isEqualTo(10_000_000-(304*15700 +43*15500));
        verify(eventPublisher).publish(new OpeningPriceEvent(LocalDateTime.now(),security.getIsin(),openingPrice,0));
    }

    
     @Test
     void find_auction_price_change_successfully_done_when_some_orders_get_removed() {
         OrderBook orderBook = security.getOrderBook();
         orderBook.removeByOrderId(Side.SELL, 7);
         int openingPrice = security.updateIndicativeOpeningPrice();
         assertThat(openingPrice).isEqualTo(15400);
     }
     @Test
     void no_opening_price_for_new_orders() {
        orderBook.removeByOrderId(Side.BUY, 1);
        orderBook.removeByOrderId(Side.BUY, 2);
        orderBook.removeByOrderId(Side.BUY, 3);
        orderBook.removeByOrderId(Side.BUY, 4);
        orderBook.removeByOrderId(Side.BUY, 5);
        orderBook.removeByOrderId(Side.SELL, 6);
        orderBook.removeByOrderId(Side.SELL, 7);
        orderBook.removeByOrderId(Side.SELL, 8);
        orderBook.removeByOrderId(Side.SELL, 9);
        orderBook.removeByOrderId(Side.SELL, 10);
        orders = Arrays.asList(
                new Order(11, security, BUY, 304, 15700, broker, shareholder,0),
                new Order(12, security, BUY, 43, 15500, broker, shareholder,0),
                new Order(13, security, SELL, 350, 15800, broker, shareholder,0),
                new Order(14, security, SELL, 1000, 15820, broker, shareholder,0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        int openingPrice = security.updateIndicativeOpeningPrice();
        assertThat(openingPrice).isEqualTo(0);
     }


     @Test
     void opening_price_calculated_successfully_when_opening_price_is_on_boundary() {
        orderBook.removeByOrderId(Side.BUY, 1);
        orderBook.removeByOrderId(Side.BUY, 2);
        orderBook.removeByOrderId(Side.BUY, 3);
        orderBook.removeByOrderId(Side.BUY, 4);
        orderBook.removeByOrderId(Side.BUY, 5);
        orderBook.removeByOrderId(Side.SELL, 6);
        orderBook.removeByOrderId(Side.SELL, 7);
        orderBook.removeByOrderId(Side.SELL, 8);
        orderBook.removeByOrderId(Side.SELL, 9);
        orderBook.removeByOrderId(Side.SELL, 10);
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder,0),
                new Order(2, security, BUY, 1000, 15400, broker, shareholder,0),
                new Order(3, security, SELL, 350, 15700, broker, shareholder,0),
                new Order(4, security, SELL, 65, 15820, broker, shareholder,0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        int openingPrice = security.updateIndicativeOpeningPrice();
        assertThat(openingPrice).isEqualTo(15700);
     }

         @Test
    void adding_new_MEQ_order_in_auction_state_successfully_rejected(){
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.AUCTION));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
            Side.BUY, 10, 700, broker.getBrokerId(), shareholder.getShareholderId(), 0, 10 )); 
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.MEQ_IS_PROHIBITED_IN_AUCTION_MODE)));
        
    }

    @Test
    void adding_new_stop_limit_order_in_auction_state_successfully_rejected(){
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.AUCTION));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 30, 500,
            broker.getBrokerId(), shareholder.getShareholderId(), 0, 0 , 10));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 200, List.of(Message.STOPLIMIT_ORDER_IN_AUCTION_MODE_ERROR)));
    }

    @Test 
    void adding_new_iceberg_order_in_auction_state_successfully_done(){
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.AUCTION));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 30, 500,
            broker.getBrokerId(), shareholder.getShareholderId(), 20, 0 , 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
    }

    @Test 
    void adding_new_order_in_auction_state_successfully_done(){
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.AUCTION));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.BUY, 30, 500,
            broker.getBrokerId(), shareholder.getShareholderId(), 0, 0 , 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 200));
    }

    @Test
    void change_match_state_from_continuous_to_continuous(){
        orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.CONTINUOUS));
        assertThat(security.getMatchingState()).isEqualTo(MatchingState.CONTINUOUS);
        verify(eventPublisher).publish(new SecurityStateChangedEvent(LocalDateTime.now() , security.getIsin() , MatchingState.CONTINUOUS));

    }

    // @Test 
    // void change_match_state_from_auction_to_continuous(){
    //     orderHandler.handleChangeMatchStateRq(ChangeMatchStateRq.changeMatchStateRq(security.getIsin(), MatchingState.CONTINUOUS));
    //     assertThat(security.getMatchingState()).isEqualTo(MatchingState.CONTINUOUS);
    //     verify(eventPublisher).publish(new SecurityStateChangedEvent(LocalDateTime.now() , security.getIsin() , MatchingState.CONTINUOUS));
    // }

    
}
