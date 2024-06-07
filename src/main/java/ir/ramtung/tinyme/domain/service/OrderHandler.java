package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.ChangeMatchStateRq;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import lombok.val;

import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Service
public class OrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    Matcher matcher;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }

    private void publishEnterOrderRqOutcome(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        switch (matchResult.outcome()) {
            case NOT_ENOUGH_CREDIT:
                publishNotEnoughCredit(enterOrderRq);
                return;
            case NOT_ENOUGH_POSITIONS:
                publishNotEnoughPositions(enterOrderRq);
                return;
            default:
                handleOrder(matchResult, enterOrderRq);
                publishStopPriceOutcome(matchResult, enterOrderRq);
        }
    }
    
    private void publishNotEnoughCredit(EnterOrderRq enterOrderRq) {
        eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
    }
    
    private void publishNotEnoughPositions(EnterOrderRq enterOrderRq) {
        eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
    }
    
    private void handleOrder(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER) {
            handleNewOrder(matchResult, enterOrderRq);
        } else {
            handleOrderUpdate(matchResult, enterOrderRq);
        }
    }
    
    private void handleNewOrder(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        if (matchResult.outcome() == MatchingOutcome.ORDER_ENQUEUED_IN_AUCTION_MODE) {
            Security currentSecurity = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            eventPublisher.publish(new OpeningPriceEvent(enterOrderRq.getSecurityIsin(),
                    currentSecurity.getIndicativeOpeningPrice(), currentSecurity.getHighestQuantity()));
        }
        eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
    }
    
    private void handleOrderUpdate(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        Security currentSecurity = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        if (currentSecurity.getMatchingState() == MatchingState.AUCTION) {
            currentSecurity.updateIndicativeOpeningPrice();
            eventPublisher.publish(new OpeningPriceEvent(enterOrderRq.getSecurityIsin(),
                    currentSecurity.getIndicativeOpeningPrice(), currentSecurity.getHighestQuantity()));
        }
    }
    
    private void publishStopPriceOutcome(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        if (matchResult.outcome() != MatchingOutcome.INACTIVE_ORDER_ENQUEUED && enterOrderRq.getStopPrice() > 0) {
            publishActivations(matchResult , enterOrderRq);
        }
        publishExecutions(matchResult , enterOrderRq);
    }

    private void publishActivations(MatchResult matchResult, EnterOrderRq enterOrderRq){
        Security currentSecurity = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        Order order = currentSecurity.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId());
        if (order != null) {
            eventPublisher.publish(new OrderActivatedEvent(order.getRequestId(), enterOrderRq.getOrderId()));
        } else {
            Trade lastTrade = matchResult.trades().getLast();
            Order matchedOrder = currentSecurity.getOrderBook().findByOrderId(lastTrade.getBuy().getSide(), lastTrade.getBuy().getOrderId());
            if (matchedOrder != null) {
                eventPublisher.publish(new OrderActivatedEvent(matchedOrder.getRequestId(), enterOrderRq.getOrderId()));
            }
        }
    }

    private void publishExecutions(MatchResult matchResult, EnterOrderRq enterOrderRq){
        if (!matchResult.trades().isEmpty() && securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin()).getMatchingState() == MatchingState.CONTINUOUS) {
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
        }
    }
    
    private void activateStopLimitOrders(Security security , EnterOrderRq enterOrderRq){
        if(security.getMatchingState() == MatchingState.CONTINUOUS){
            execInactiveStopLimitOrders(security , enterOrderRq);
        }
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validateOrder(enterOrderRq);
            processOrder(enterOrderRq);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void validateOrder(EnterOrderRq enterOrderRq) throws InvalidRequestException{
        ValidateRq validateRq = new ValidateRq(enterOrderRq, securityRepository, brokerRepository, shareholderRepository);
        validateRq.validateEnterOrderRq(enterOrderRq);
    }
    
    private void processOrder(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
        Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());
    
        MatchResult matchResult;
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            matchResult = security.newOrder(enterOrderRq, broker, shareholder, matcher);
        else
            matchResult = security.updateOrder(enterOrderRq, matcher);
    
        publishEnterOrderRqOutcome(matchResult, enterOrderRq);
        activateStopLimitOrders(security, enterOrderRq);
    }
    
    private void execInactiveStopLimitOrders(Security security, EnterOrderRq enterOrderRq) {
        while (true) {
            Order executableOrder = security.getOrderBook().dequeueNextStopLimitOrder(enterOrderRq.getSide());
            if (executableOrder == null) {
                break;
            }
            processExecutableOrder(executableOrder, enterOrderRq);
        }
    }
    
    private void execInactiveStopLimitOrders(Security security) {
        while (true) {
            Order executableBuyOrder = security.getOrderBook().dequeueNextStopLimitOrder(Side.BUY);
            Order executableSellOrder = security.getOrderBook().dequeueNextStopLimitOrder(Side.SELL);
            if (executableBuyOrder == null && executableSellOrder == null) {
                break;
            }
            if (executableBuyOrder != null) {
                processExecutableOrder(executableBuyOrder, null);
            }
            if (executableSellOrder != null) {
                processExecutableOrder(executableSellOrder, null);
            }
        }
    }
    
    private void processExecutableOrder(Order executableOrder, EnterOrderRq enterOrderRq) {
        executableOrder.getBroker().increaseCreditBy(executableOrder.getValue());
        MatchResult matchResult = matcher.execute(executableOrder);
        if (matchResult.outcome() != MatchingOutcome.INACTIVE_ORDER_ENQUEUED && executableOrder.getStopPrice() > 0) {
            eventPublisher.publish(new OrderActivatedEvent(executableOrder.getRequestId(), executableOrder.getOrderId()));
        }
        publishTrades(matchResult, enterOrderRq, executableOrder);
    }

    private void publishTrades(MatchResult matchResult, EnterOrderRq enterOrderRq, Order executableOrder){
        if (!matchResult.trades().isEmpty()) {
            eventPublisher.publish(new OrderExecutedEvent(
                    enterOrderRq != null ? enterOrderRq.getRequestId() : executableOrder.getRequestId(),
                    executableOrder.getOrderId(),
                    matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())
            ));
        }
    }
    
    private void execInactiveStopLimitOrdersAuction(Security security) {
        while (true) {
            Order executableBuyOrder = security.getOrderBook().dequeueNextStopLimitOrder(Side.BUY);
            Order executableSellOrder = security.getOrderBook().dequeueNextStopLimitOrder(Side.SELL);
            if (executableBuyOrder == null && executableSellOrder == null) {
                break;
            }
            processAuctionOrder(executableBuyOrder, Side.BUY);
            processAuctionOrder(executableSellOrder, Side.SELL);
        }
    }
    
    private void processAuctionOrder(Order executableOrder, Side side) {
        if (executableOrder != null) {
            if (side == Side.BUY) {
                executableOrder.getBroker().increaseCreditBy(executableOrder.getValue());
            }
            MatchResult matchResult = matcher.auctionAddToQueue(executableOrder);
            if (matchResult.outcome() != MatchingOutcome.INACTIVE_ORDER_ENQUEUED && executableOrder.getStopPrice() > 0) {
                eventPublisher.publish(new OrderActivatedEvent(executableOrder.getRequestId(), executableOrder.getOrderId()));
            }
        }
    }
    
    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            if(security.getMatchingState() == MatchingState.AUCTION && security.getOrderBook().findInActiveByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId()) != null){
                eventPublisher.publish(new OrderRejectedEvent(2, 200, List.of(Message.STOPLIMIT_ORDER_IN_AUCTION_MODE_CANT_REMOVE)));
            }
            else{
                eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
            }
            if(security.getMatchingState() == MatchingState.AUCTION){
                security.updateIndicativeOpeningPrice();
                eventPublisher.publish(new OpeningPriceEvent(security.getIsin() , security.getIndicativeOpeningPrice() , security.getHighestQuantity()));
            }
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        ValidateRq validateRq = new ValidateRq(null, securityRepository, brokerRepository, shareholderRepository);
        validateRq.validateDeleteOrderRq(deleteOrderRq);
    }

    public void handleChangeMatchStateRq(ChangeMatchStateRq changeMatchStateRq) {
        Security security = securityRepository.findSecurityByIsin(changeMatchStateRq.getSecurityIsin());
        MatchingState matchingState = changeMatchStateRq.getState();
        MatchResult matchResult = security.ChangeMatchStateRq(matchingState, matcher);
        
        processMatchStateChange(security, matchResult);
    }
    
    private void processMatchStateChange(Security security, MatchResult matchResult) {
        if (security.getMatchingState() == MatchingState.CONTINUOUS) {
            execInactiveStopLimitOrders(security);
        } else {
            execInactiveStopLimitOrdersAuction(security);
        }
        if (matchResult != null) {
            eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), security.getIndicativeOpeningPrice(), security.getHighestQuantity()));
        }
        if (matchResult != null && !matchResult.trades().isEmpty()) {
            matchResult.trades().forEach(trade -> eventPublisher.publish(new TradeEvent(security.getIsin(), trade.getPrice(), trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId())));
        }
        eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), security.getMatchingState()));
    }
    
}
