package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.ListIterator;

@Service
public class Matcher {
    private int indicativeOpeningPrice =0 ; ///best auction price
    
    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();
        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            // if ((newOrder instanceof StopLimitOrder) && ((StopLimitOrder) newOrder).getIsActive() == false){
            //     orderBook.stopLimitOrderEnqueue((StopLimitOrder)newOrder);
            //     break;
            // }
            if (matchingOrder == null)
                break;

            Trade trade = new Trade(newOrder.getSecurity(), matchingOrder.getPrice(), Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);
            if (newOrder.getSide() == Side.BUY) {
                if (trade.buyerHasEnoughCredit() )
                    trade.decreaseBuyersCredit();
                else {
                    rollbackBuyTrades(newOrder, trades);
                    return MatchResult.notEnoughCredit();
                }
            }
            trade.increaseSellersCredit();
            trades.add(trade);

            if (newOrder.getQuantity() >= matchingOrder.getQuantity()) {
                newOrder.decreaseQuantity(matchingOrder.getQuantity());
                orderBook.removeFirst(matchingOrder.getSide());
                if (matchingOrder instanceof IcebergOrder icebergOrder) {
                    icebergOrder.decreaseQuantity(matchingOrder.getQuantity());
                    icebergOrder.replenish();
                    if (icebergOrder.getQuantity() > 0)
                        orderBook.enqueue(icebergOrder);
                }
            } else {
                matchingOrder.decreaseQuantity(newOrder.getQuantity());
                newOrder.makeQuantityZero();
            }
        }
        if (matchBasedOnMinimumExecutionQuantity(newOrder, trades)){
            return MatchResult.executed(newOrder, trades);
        }  
        else{
            return MatchResult.notEnoughQuantitiesMatched();
        }     
    }

    private void rollbackBuyTrades(Order newOrder, LinkedList<Trade> trades) {
        assert newOrder.getSide() == Side.BUY;
        newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreSellOrder(it.previous().getSell());
        }
    }

    private void rollbackSellTrades(Order newOrder, LinkedList<Trade> trades) {
        assert newOrder.getSide() == Side.SELL;
        newOrder.getBroker().decreaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        trades.forEach(trade -> trade.getBuy().getBroker().increaseCreditBy(trade.getTradedValue()));

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreBuyOrder(it.previous().getBuy());
        }
    }

    public MatchResult execute(Order order) {
        MatchResult result = match(order);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return result;
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_QUANTITIES_MATCHED)
            return result;

        if (((result.remainder().getQuantity() > 0))) {
            if (order.getSide() == Side.BUY) {
                if (!order.getBroker().hasEnoughCredit(order.getValue()) && !(order instanceof StopLimitOrder)) {
                    rollbackBuyTrades(order, result.trades());
                    return MatchResult.notEnoughCredit();
                }
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }
        if (!result.trades().isEmpty()) {
            order.getSecurity().getOrderBook().setLastTradePrice(result.trades().getLast().getPrice());
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
        return result;
    }

    private boolean matchBasedOnMinimumExecutionQuantity(Order newOrder, LinkedList<Trade> trades) {
        int sumOfTradeQuantities = calculateSumOfTradeQuantities(trades);
        if (newOrder.getMinimumExecutionQuantity() > sumOfTradeQuantities){
            if (newOrder.getSide() == Side.SELL){
                rollbackSellTrades(newOrder, trades);
            }
            else if (newOrder.getSide() == Side.BUY && !(newOrder instanceof StopLimitOrder)){
                rollbackBuyTrades(newOrder, trades);
            }
            
            
            return false;
        }
        else
            return true;
    }

    private int calculateSumOfTradeQuantities(LinkedList<Trade> trades) {
        return trades.stream()
                 .mapToInt(Trade::getQuantity)
                 .sum();
    }

    public int handleAuctionPrice(Security security ){

        OrderBook orderBook = security.getOrderBook(); 
        LinkedList<Order> buyQueue = orderBook.getQueue(Side.BUY);
        LinkedList<Order> sellQueue = orderBook.getQueue(Side.SELL);
        LinkedList <Integer> allOrdersPrices = new LinkedList<>() ;

        for (Order buyOrder : buyQueue) {
            allOrdersPrices.add(buyOrder.getPrice());
        }
        for (Order sellOrder : sellQueue) {
            allOrdersPrices.add(sellOrder.getPrice());
        }
       
        return findBestAuctionPrice(allOrdersPrices,buyQueue,sellQueue);
    }


    public int findBestAuctionPrice(LinkedList <Integer> allOrdersPrices,LinkedList<Order> buyQueue,LinkedList<Order> sellQueue){  //  function that input : price , output: quantity  -- we update max quantity each time in loop
        int highestQuantity=0;
        int sumOfSellQuantities=0;
        int sumOfBuyQuantities=0;
        int highestQuantityForOnePrice = 0;
        int correspondingPrice=0;
        for(Integer orderPrice : allOrdersPrices){
            for(Order sellOrder : sellQueue){
                if(sellOrder.getPrice()<= orderPrice){
                    sumOfSellQuantities += sellOrder.getQuantity();
                }
            }

            for(Order buyOrder : buyQueue){
                if(buyOrder.getPrice()<= orderPrice){
                    sumOfBuyQuantities += buyOrder.getQuantity();
                }
            }

            highestQuantityForOnePrice= Math.min(sumOfBuyQuantities,sumOfSellQuantities);

            if (highestQuantityForOnePrice > highestQuantity) {
                highestQuantity = highestQuantityForOnePrice;
                correspondingPrice = orderPrice;
            }

        }

        return correspondingPrice;

    }


}
