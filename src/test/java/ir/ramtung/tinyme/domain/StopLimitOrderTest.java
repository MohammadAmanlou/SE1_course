package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class StopLimitOrderTest {
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    List <EventPublisher> eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private Shareholder shareholder;
    private Broker broker1;
    private Broker broker2;
    private Broker broker3;


    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);


        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        broker1 = Broker.builder().brokerId(1).credit(0).build();
        broker2 = Broker.builder().brokerId(2).credit(0).build();
        broker3 = Broker.builder().brokerId(3).credit(0).build();
        brokerRepository.addBroker(broker1);
        brokerRepository.addBroker(broker2);
        brokerRepository.addBroker(broker3);
    }

    @Test
    void update_stop_price_for_buy_non_stop_limit_order_successfully_rejected() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0 );
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.BUY, 30, 500,
         broker1.getBrokerId(), shareholder.getShareholderId(), 0, 0 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        verify(eventPublisher.get(0)).publish(new OrderRejectedEvent(1, 100, List.of(Message.UPDATING_REJECTED_BECAUSE_IT_IS_NOT_STOP_LIMIT_ORDER)));
    }

    @Test
    void update_stop_price_for_sell_non_stop_limit_order_successfully_rejected() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new Order(100, security, Side.SELL, 30, 500, broker1, shareholder,0 );
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.SELL, 30, 500,
                broker1.getBrokerId(), shareholder.getShareholderId(), 0, 0 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        verify(eventPublisher.get(0)).publish(new OrderRejectedEvent(1, 100, List.of(Message.UPDATING_REJECTED_BECAUSE_IT_IS_NOT_STOP_LIMIT_ORDER)));
    }

    @Test
    void update_peak_size_for_buy_stop_limit_order_successfully_rejected() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new StopLimitOrder(100, security, Side.BUY, 30, 500, broker1, shareholder,
         10 , 0 );
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.BUY, 30, 500,
         broker1.getBrokerId(), shareholder.getShareholderId(), 10, 0 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        verify(eventPublisher.get(0)).publish(new OrderRejectedEvent(1, 100, List.of(Message.STOP_LIMIT_ORDER_CANT_ICEBERG)));
    }

    @Test
    void update_peak_size_for_sell_stop_limit_order_successfully_rejected() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new StopLimitOrder(100, security, Side.SELL, 30, 500, broker1, shareholder,
                10 , 0 );
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.SELL, 30, 500,
                broker1.getBrokerId(), shareholder.getShareholderId(), 10, 0 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        verify(eventPublisher.get(0)).publish(new OrderRejectedEvent(1, 100, List.of(Message.STOP_LIMIT_ORDER_CANT_ICEBERG)));
    }

    @Test
    void update_MEQ_for_buy_stop_limit_order_successfully_rejected() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new StopLimitOrder(100, security, Side.BUY, 30, 500, broker1, shareholder, 10 , 0 );
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.BUY, 30, 550,
         broker1.getBrokerId(), shareholder.getShareholderId(), 0, 10 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        verify(eventPublisher.get(0)).publish(new OrderRejectedEvent(1, 100, List.of(Message.STOP_LIMIT_ORDER_CANT_MEQ)));
    }

    @Test
    void update_MEQ_for_sell_stop_limit_order_successfully_rejected() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new StopLimitOrder(100, security, Side.SELL, 30, 500, broker1, shareholder, 10 , 0 );
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.SELL, 30, 550,
                broker1.getBrokerId(), shareholder.getShareholderId(), 0, 10 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        verify(eventPublisher.get(0)).publish(new OrderRejectedEvent(1, 100, List.of(Message.STOP_LIMIT_ORDER_CANT_MEQ)));
    }

    @Test
    void having_MEQ_for_buy_stop_limit_order_successfully_rejected() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new StopLimitOrder(100, security, Side.BUY, 30, 500, broker1, shareholder,
         10 , 10);
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.BUY, 30, 550,
        broker1.getBrokerId(), shareholder.getShareholderId(), 0, 10 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        verify(eventPublisher.get(0)).publish(new OrderRejectedEvent(1, 100, List.of(Message.STOP_LIMIT_ORDER_CANT_MEQ)));
    }

    @Test
    void having_MEQ_for_sell_stop_limit_order_successfully_rejected() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new StopLimitOrder(100, security, Side.SELL, 30, 500, broker1, shareholder,
                10 , 10);
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.SELL, 30, 550,
                broker1.getBrokerId(), shareholder.getShareholderId(), 0, 10 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000);
        verify(eventPublisher.get(0)).publish(new OrderRejectedEvent(1, 100, List.of(Message.STOP_LIMIT_ORDER_CANT_MEQ)));
    }

    @Test
    void new_stopLimit_not_matching() { //new_order_from_buyer_with_enough_credit_based_on_trades
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_500).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);
     
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(),
        0 , 0 , 600));

        assertThat(broker1.getCredit()).isEqualTo(104_000 );
        assertThat(broker2.getCredit()).isEqualTo(96000 );
        assertThat(broker3.getCredit()).isEqualTo(520_500);
    }

    @Test
    void prev_SLO_matched_completely_with_one_trade() {
        Order matchingBuyOrder = new StopLimitOrder(100, security, Side.BUY, 1000, 15500, broker1, shareholder,   10 , 0);
        Order incomingSellOrder = new StopLimitOrder(200, security, Side.SELL, 300, 15450, broker2, shareholder,   10 , 0);
        security.getOrderBook().enqueue(matchingBuyOrder);
        

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), Side.SELL, 
        300, 15450, broker2.getBrokerId(), shareholder.getShareholderId(), 0, 100));
        Trade trade = new Trade(security, matchingBuyOrder.getPrice(), incomingSellOrder.getQuantity(),
        matchingBuyOrder, incomingSellOrder);
        
        assertThat(broker1.getCredit()).isEqualTo(0 );
        assertThat(broker2.getCredit()).isEqualTo(4650000 );
        verify(eventPublisher.get(0)).publish((new OrderAcceptedEvent(1, 200)));
        verify(eventPublisher.get(0)).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade))));
    }


    @Test
    void prev_SLO_from_buyer_matching_partly() { 
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_500).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        Order order1 = new StopLimitOrder(100, security, Side.SELL, 30, 500, broker1, shareholder,   10 , 0);
        Order order2 = new StopLimitOrder(110, security, Side.SELL, 20, 400, broker2, shareholder,   10 , 0);
        security.getOrderBook().enqueue(order1);
        security.getOrderBook().enqueue(order2);
     
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 200, 950, broker3.getBrokerId(), shareholder.getShareholderId(), 0,100));

        assertThat(broker1.getCredit()).isEqualTo(100_000 );
        assertThat(broker2.getCredit()).isEqualTo(100_000 );
        assertThat(broker3.getCredit()).isEqualTo(520_500);

        verify(eventPublisher.get(0)).publish(new OrderAcceptedEvent(1, 200));

    }

    @Test
    void prev_SLO_from_buyer_matching_all_new_MEQ_order() { //new_order_from_buyer_with_enough_credit_based_on_trades
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_500).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order order1 = new StopLimitOrder(100, security, Side.SELL, 30, 500, broker1, shareholder,   10 , 0);
        Order order2 = new StopLimitOrder(110, security, Side.SELL, 20, 400, broker2, shareholder,   10 , 0);

        security.getOrderBook().enqueue(order1);
        security.getOrderBook().enqueue(order2);
     
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 100, 950, broker3.getBrokerId(), shareholder.getShareholderId(), 0,10));

        assertThat(broker1.getCredit()).isEqualTo(115000 );
        assertThat(broker2.getCredit()).isEqualTo(108_000 );
        assertThat(broker3.getCredit()).isEqualTo(450000);

        verify(eventPublisher.get(0)).publish(new OrderAcceptedEvent(1, 200));
    }

    @Test
    void prev_SLO_matched_with_one_trade() {
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);
        Order order = new StopLimitOrder(100, security, Side.BUY, 30, 500, broker1, shareholder,   10 , 0);
        security.getOrderBook().enqueue(order);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 100, LocalDateTime.now(),    Side.BUY,
         50, 550, broker1.getBrokerId(), shareholder.getShareholderId(), 0,1));

        assertThat(broker1.getCredit()).isEqualTo(100_000 );
        assertThat(broker2.getCredit()).isEqualTo(0 );
        verify(eventPublisher.get(0)).publish(new OrderAcceptedEvent(1, 100));
    }

    @Test
    void new_stopLimit_Matching_changing_lastTradePrice() { //new_order_from_buyer_with_enough_credit_based_on_trades
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_500).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        Order matchingSellOrder1 = new Order(100, security, Side.SELL, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 400, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker3.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 ));
     
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 900, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 800));



        assertThat(broker1.getCredit()).isEqualTo(108000 );
        assertThat(broker2.getCredit()).isEqualTo(96000 );
        assertThat(broker3.getCredit()).isEqualTo(516500);
    }

    @Test
    void new_stopLimit_notMatching_changing_lastTradePrice() { 
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_500).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));
        Order matchingSellOrder1 = new Order(100, security, Side.SELL, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 400, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker3.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 ));
     
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 200));


        assertThat(broker1.getCredit()).isEqualTo(104000 );
        assertThat(broker2.getCredit()).isEqualTo(93000 );
        assertThat(broker3.getCredit()).isEqualTo(516500);

    }

    @Test
    void delete_inactive_buy_stop_limit_order_deletes_successfully_and_increases_credit() {
        Broker buyBroker = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(buyBroker);
        Order order = new StopLimitOrder(100, security, Side.BUY, 30, 500, buyBroker, shareholder,   10 , 0);
        security.getOrderBook().enqueue(order);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.BUY, 100));
        verify(eventPublisher.get(0)).publish(new OrderDeletedEvent(1, 100));
        assertThat(buyBroker.getCredit()).isEqualTo(100_000 + 30*500);
    }

    @Test
    void delete_inactive_sell_stop_limit_order_deletes_successfully_and_increases_credit() {
        Broker sellBroker = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(sellBroker);
        Order order = new StopLimitOrder(100, security, Side.SELL, 30, 500, sellBroker, shareholder,   10 , 0);
        security.getOrderBook().enqueue(order);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.SELL, 100));
        verify(eventPublisher.get(0)).publish(new OrderDeletedEvent(1, 100));
        assertThat(sellBroker.getCredit()).isEqualTo(100_000);
    }


    @Test
    void delete_buy_active_stop_limit_order_deletes_successfully_and_increases_credit(){


    }

    @Test
    void update_quantity_for_inactive_buy_stop_limit_order_successfully_done(){
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);

        Order stopLimitOrder = new StopLimitOrder(100, security, Side.BUY, 10, 500, broker1, shareholder,   10 , 0);
        int prevQuantity = stopLimitOrder.getQuantity();
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), Side.BUY, 50, 500,
                broker1.getBrokerId(), shareholder.getShareholderId(), 0, 0 , 10));

        assertThat(broker1.getCredit()).isEqualTo(100_000 - Math.abs(stopLimitOrder.getQuantity() - prevQuantity) * stopLimitOrder.getPrice() );
    }

    @Test
    void update_quantity_for_inactive_sell_stop_limit_order_successfully_done(){
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(),
                Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(),
                0 , 0 , 200));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 300, LocalDateTime.now(),
                Side.BUY, 10, 600, broker3.getBrokerId(), shareholder.getShareholderId(),
                0 , 0 , 300));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(3, "ABC", 300, LocalDateTime.now(), Side.BUY, 10, 300,
                broker1.getBrokerId(), shareholder.getShareholderId(), 0, 0 , 500));

        assertThat(broker1.getCredit()).isEqualTo(104_000);
    }

    @Test
    void update_price_for_inactive_buy_stop_limit_order_successfully_done(){
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(),
                Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(),
                0 , 0 , 200));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 300, LocalDateTime.now(),
                Side.BUY, 10, 600, broker3.getBrokerId(), shareholder.getShareholderId(),
                0 , 0 , 300));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(3, "ABC", 300, LocalDateTime.now(), Side.BUY, 10, 300,
                broker1.getBrokerId(), shareholder.getShareholderId(), 0, 0 , 500));

        assertThat(broker1.getCredit()).isEqualTo(104_000);
    }

    @Test
    void update_price_for_inactive_sell_stop_limit_order_successfully_done(){
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(),
                Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(),
                0 , 0 , 200));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 300, LocalDateTime.now(),
                Side.SELL, 10, 600, broker3.getBrokerId(), shareholder.getShareholderId(),
                0 , 0 , 500));

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(3, "ABC", 300, LocalDateTime.now(), Side.SELL, 10, 300,
                broker1.getBrokerId(), shareholder.getShareholderId(), 0, 0 , 500));

        assertThat(broker1.getCredit()).isEqualTo(104_000);
        assertThat(security.getOrderBook().findInActiveByOrderId(Side.SELL , 300, 500, false).getPrice()).isEqualTo(300);
        //assertThat(security.getOrderBook().findByOrderId(Side.BUY, 1))
        //                .isEqualTo(orders.get(0));
    }

    @Test
    void update_stop_price_for_inactive_sell_stop_limit_order_successfully_done(){
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        brokerRepository.addBroker(broker1);

        Order stopLimitOrder = new StopLimitOrder(100, security, Side.SELL, 10, 500, broker1, shareholder,   10 , 0);
        security.getOrderBook().enqueue(stopLimitOrder);
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 100, LocalDateTime.now(), 
        Side.SELL, 10, 500,broker1.getBrokerId(), shareholder.getShareholderId(),
         0, 0 , 50));
        verify(eventPublisher.get(0)).publish(new OrderUpdatedEvent(1,100));
        assertThat(broker1.getCredit()).isEqualTo(100_000);
        //assertThat(security.getOrderBook().findActiveByOrderId(Side.SELL, 100, 10, false).getStopPrice()).isEqualTo(50);
    }

    @Test
    void update_stop_price_for_active_buy_stop_limit_order_failed(){
        Broker broker1 = Broker.builder().brokerId(1).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(2).credit(100_000).build();
        List.of(broker1, broker2).forEach(b -> brokerRepository.addBroker(b));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 10, LocalDateTime.now(), Side.BUY,
                30, 500, broker1.getBrokerId(), shareholder.getShareholderId(), 0 , 0 , 0));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 20, LocalDateTime.now(), Side.SELL,
                30, 500, broker2.getBrokerId(), shareholder.getShareholderId(), 0 , 0, 0 ));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "ABC", 30, LocalDateTime.now(), Side.BUY,
                30, 500, broker1.getBrokerId(), shareholder.getShareholderId(), 0, 0, 100));





    }
    //shzd
    @Test 
    void buy_SLO_activates_because_init_stop_price_is_zero(){ //checked
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_500).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.BUY, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 200));
        assertThat(broker1.getCredit()).isEqualTo(100_000 );
        assertThat(broker2.getCredit()).isEqualTo(93_000 );
        assertThat(broker3.getCredit()).isEqualTo(520500);  
    }

    @Test
    void buy_SLO1_activates_another_buy_SLO2_because_stop_price2_is_greater_than_trade_price(){ //not_checked
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.BUY, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 200));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 300, LocalDateTime.now(), 
        Side.BUY, 10, 200, broker3.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 300));
        assertThat(broker1.getCredit()).isEqualTo(100_000 );
        assertThat(broker2.getCredit()).isEqualTo(93_000 );
        assertThat(broker3.getCredit()).isEqualTo(518_000);  
    }

    @Test
    void buy_SLO1_not_activates_another_buy_SLO2_because_stop_price2_is_less_than_trade_price(){ //not checked
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 200));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 300, LocalDateTime.now(), 
        Side.BUY, 10, 500, broker3.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 100));
        assertThat(broker1.getCredit()).isEqualTo(104_000 );
        assertThat(broker2.getCredit()).isEqualTo(96_000 );
        assertThat(broker3.getCredit()).isEqualTo(515_000);  
    }

    @Test 
    void buy_SLO_not_get_activated_because_one_order_with_greater_stop_price_comes_after_it(){ //checked
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 200));


        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 300, LocalDateTime.now(), 
        Side.BUY, 10, 500, broker3.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 300));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "ABC", 400, LocalDateTime.now(), 
        Side.SELL, 10, 150, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 ));

        assertThat(broker1.getCredit()).isEqualTo(104_000 );
        assertThat(broker2.getCredit()).isEqualTo(101_000 );
        assertThat(broker3.getCredit()).isEqualTo(515_000);  
    }

    @Test 
    void buy_SLO_activates_because_one_order_with_greater_stop_price_comes_after_it(){ //not checked
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 200));


        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 300, LocalDateTime.now(), 
        Side.BUY, 10, 600, broker3.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 500));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "ABC", 400, LocalDateTime.now(), 
        Side.SELL, 10, 150, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 ));

        assertThat(broker1.getCredit()).isEqualTo(108_000 );
        assertThat(broker2.getCredit()).isEqualTo(101_000 );
        assertThat(broker3.getCredit()).isEqualTo(516_000);  
    }
/* 
    @Test 
    void sell_SLO_activates_because_one_order_with_less_stop_price_comes_after_it(){ //not checked
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_000).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.SELL, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 200));


        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 300, LocalDateTime.now(), 
        Side.BUY, 10, 600, broker3.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 500));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(3, "ABC", 400, LocalDateTime.now(), 
        Side.SELL, 10, 150, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 ));

        assertThat(broker1.getCredit()).isEqualTo(108_000 );
        assertThat(broker2.getCredit()).isEqualTo(101_000 );
        assertThat(broker3.getCredit()).isEqualTo(516_000);  
    }

///
///
    @Test
    void SLO_stays_deactivated_after_one_order_gets_accepted(){
        Broker broker1 = Broker.builder().brokerId(10).credit(100_000).build();
        Broker broker2 = Broker.builder().brokerId(20).credit(100_000).build();
        Broker broker3 = Broker.builder().brokerId(30).credit(520_500).build();
        List.of(broker1, broker2, broker3).forEach(b -> brokerRepository.addBroker(b));

        Order matchingSellOrder1 = new Order(100, security, Side.BUY, 30, 500, broker1, shareholder,0);
        Order matchingSellOrder2 = new Order(110, security, Side.BUY, 20, 400, broker1, shareholder,0);
        security.getOrderBook().enqueue(matchingSellOrder1);
        security.getOrderBook().enqueue(matchingSellOrder2);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(2, "ABC", 200, LocalDateTime.now(), 
        Side.BUY, 10, 700, broker2.getBrokerId(), shareholder.getShareholderId(), 
        0 , 0 , 200));
        assertThat(broker1.getCredit()).isEqualTo(100_000 );
        assertThat(broker2.getCredit()).isEqualTo(100_000 );
    }

    @Test
    void SLO_stays_activated_after_updating_its_price(){}

    @Test
    void few_SLOs_get_activated_after_one_order_(){}

*/
}
