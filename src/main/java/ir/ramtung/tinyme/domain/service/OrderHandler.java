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

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            if(security.getMatchingState() == MatchingState.AUCTION && security.getOrderBook().findInActiveByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId()) != null){
                publishOrderRejectedEvent(2, 200, List.of(Message.STOPLIMIT_ORDER_IN_AUCTION_MODE_CANT_REMOVE));
            }
            else{
                publishOrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId());
            }
            if(security.getMatchingState() == MatchingState.AUCTION){
                security.updateIndicativeOpeningPrice();
                publishOpeningPriceEvent(security);
            }
        } catch (InvalidRequestException ex) {
            publishOrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons());
        }
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        try {
            validateAndProcessOrder(enterOrderRq);
        } catch (InvalidRequestException ex) {
            publishOrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons());
        }
    }

    public void handleChangeMatchStateRq(ChangeMatchStateRq changeMatchStateRq) {
        Security security = securityRepository.findSecurityByIsin(changeMatchStateRq.getSecurityIsin());
        MatchingState matchingState = changeMatchStateRq.getState();
        MatchResult matchResult = security.ChangeMatchStateRq(matchingState, matcher);
        
        processMatchStateChange(security, matchResult);
    }

    private void publishEvent(Event event) {
        eventPublisher.publish(event);
    }
    private void publishNotEnoughCredit(EnterOrderRq enterOrderRq) {
        publishEvent(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
    }
    
    private void publishNotEnoughPositions(EnterOrderRq enterOrderRq) {
        publishEvent(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
    }

    private void publishOpeningPriceEvent(Security security) {
        publishEvent(new OpeningPriceEvent(security.getIsin(), security.getIndicativeOpeningPrice(), security.getHighestQuantity()));
    }

    private void publishOpeningPriceEvent(Security security, EnterOrderRq enterOrderRq) {
        publishEvent(new OpeningPriceEvent(enterOrderRq.getSecurityIsin(), security.getIndicativeOpeningPrice(), security.getHighestQuantity()));
    }

    private void publishOrderAcceptedEvent(long requestId, long orderId) {
        publishEvent(new OrderAcceptedEvent(requestId, orderId));
    }

    private void publishOrderUpdatedEvent(long requestId, long orderId) {
        eventPublisher.publish(new OrderUpdatedEvent(requestId, orderId));
    }

    private void publishOrderActivatedEvent(long requestId, long orderId) {
        eventPublisher.publish(new OrderActivatedEvent(requestId, orderId));
    }

    private void publishOrderExecutedEvent(long requestId, long orderId, List<TradeDTO> trades) {
        publishEvent(new OrderExecutedEvent(requestId, orderId, trades));
    }
    
    private void publishOrderRejectedEvent(long requestId, long orderId, List<String> reasons) {
        eventPublisher.publish(new OrderRejectedEvent(requestId, orderId, reasons));
    }

    private void publishOrderDeletedEvent(long requestId, long orderId) {
        eventPublisher.publish(new OrderDeletedEvent(requestId, orderId));
    }

    private void publishTradeEvent(String securityIsin, Trade trade) {
        publishEvent(new TradeEvent(securityIsin, trade.getPrice(), trade.getQuantity(), trade.getBuy().getOrderId(), trade.getSell().getOrderId()));
    }

    private void publishSecurityStateChangedEvent(String securityIsin, MatchingState matchingState) {
        eventPublisher.publish(new SecurityStateChangedEvent(securityIsin, matchingState));
    }

    private void publishOutcome(MatchResult matchResult, EnterOrderRq enterOrderRq) {
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

    private void publishStopPriceOutcome(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        if (matchResult.outcome() != MatchingOutcome.INACTIVE_ORDER_ENQUEUED && enterOrderRq.getStopPrice() > 0) {
            publishActivations(matchResult , enterOrderRq);
        }
        publishExecutions(matchResult , enterOrderRq);
    }

    private void publishExecutions(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        if (!matchResult.trades().isEmpty() && 
            securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin()).getMatchingState() == MatchingState.CONTINUOUS) {
            publishOrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList()));
        }
    }

    private void publishActivations(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        Security currentSecurity = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        Order order = currentSecurity.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId());
        if (order != null) {
            publishOrderActivatedEvent(order.getRequestId(), enterOrderRq.getOrderId());
        } else {
            Trade lastTrade = matchResult.trades().getLast();
            Order matchedOrder = currentSecurity.getOrderBook().findByOrderId(lastTrade.getBuy().getSide(), lastTrade.getBuy().getOrderId());
            if (matchedOrder != null) {
                publishOrderActivatedEvent(matchedOrder.getRequestId(), enterOrderRq.getOrderId());
            }
        }
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
            publishOpeningPriceEvent(currentSecurity, enterOrderRq);
        }
        publishOrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId());
    }

    private void handleOrderUpdate(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        Security currentSecurity = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        publishOrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId());
        if (currentSecurity.getMatchingState() == MatchingState.AUCTION) {
            currentSecurity.updateIndicativeOpeningPrice();
            publishOpeningPriceEvent(currentSecurity, enterOrderRq);
        }
    }
    
    private void handleStopPriceAndTrades(MatchResult matchResult, EnterOrderRq enterOrderRq) {
        if (matchResult.outcome() != MatchingOutcome.INACTIVE_ORDER_ENQUEUED && enterOrderRq.getStopPrice() > 0) {
            Security currentSecurity = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Order order = currentSecurity.getOrderBook().findByOrderId(enterOrderRq.getSide(), enterOrderRq.getOrderId());
            if (order != null) {
                publishOrderActivatedEvent(order.getRequestId(), enterOrderRq.getOrderId());
            } else {
                Trade lastTrade = matchResult.trades().getLast();
                Order matchedOrder = currentSecurity.getOrderBook().findByOrderId(lastTrade.getBuy().getSide(), lastTrade.getBuy().getOrderId());
                if (matchedOrder != null) {
                    publishOrderActivatedEvent(matchedOrder.getRequestId(), enterOrderRq.getOrderId());
                }
            }
        }
        if (!matchResult.trades().isEmpty() && securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin()).getMatchingState() == MatchingState.CONTINUOUS) {
            publishOrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
             matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList()));
        }
    }
    
    private void activateStopLimitOrders(Security security , EnterOrderRq enterOrderRq){
        if(security.getMatchingState() == MatchingState.CONTINUOUS){
            execInactiveStopLimitOrders(security , enterOrderRq);
        }
    }

    
    
    private void validateAndProcessOrder(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        ValidateRq validateRq = new ValidateRq(enterOrderRq, securityRepository, brokerRepository, shareholderRepository);
        validateRq.validateEnterOrderRq(enterOrderRq);
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
        Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());
    
        MatchResult matchResult;
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            matchResult = security.newOrder(enterOrderRq, broker, shareholder, matcher);
        else
            matchResult = security.updateOrder(enterOrderRq, matcher);
    
        publishOutcome(matchResult, enterOrderRq);
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
            publishOrderActivatedEvent(executableOrder.getRequestId(), executableOrder.getOrderId());
        }
        if (!matchResult.trades().isEmpty()) {
            publishOrderExecutedEvent(
                    enterOrderRq != null ? enterOrderRq.getRequestId() : executableOrder.getRequestId(),
                    executableOrder.getOrderId(),
                    matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())
            );
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
                publishOrderActivatedEvent(executableOrder.getRequestId(), executableOrder.getOrderId());
            }
        }
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private void processMatchStateChange(Security security, MatchResult matchResult) {
        if (security.getMatchingState() == MatchingState.CONTINUOUS) {
            execInactiveStopLimitOrders(security);
        } else {
            execInactiveStopLimitOrdersAuction(security);
        }
        if (matchResult != null) {
            publishOpeningPriceEvent(security);
        }
        if (matchResult != null && !matchResult.trades().isEmpty()) {
            matchResult.trades().forEach(trade -> publishTradeEvent(security.getIsin(), trade));
        }
        publishSecurityStateChangedEvent(security.getIsin(), security.getMatchingState());
    }

}