package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.ValidateRq;
import ir.ramtung.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.ObjectUtils.Null;

import java.util.ArrayList;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private ArrayList<MatchResult> matchResults = new ArrayList<>();
    @Builder.Default
    private MatchingState matchingState = MatchingState.CONTINUOUS;
    @Builder.Default
    private int indicativeOpeningPrice = 0 ; ///best auction price
    @Builder.Default
    private int highestQuantity = 0;

    private boolean checkPosition(EnterOrderRq enterOrderRq , Shareholder shareholder){
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                        orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity())) {
            return false;
        }
        else{
            return true;
        }
    }

    private Order makeNewOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder){
        Order order;
        if ((enterOrderRq.getPeakSize() == 0) && (enterOrderRq.getStopPrice() == 0)){
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(),OrderStatus.NEW ,enterOrderRq.getMinimumExecutionQuantity());
        }
        else if (enterOrderRq.getStopPrice() != 0){
            order = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getStopPrice() );
            order.setRequestId(enterOrderRq.getRequestId());
        }
        else {
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), OrderStatus.NEW,enterOrderRq.getMinimumExecutionQuantity());
        }
        return order;
    }

    private MatchResult impossibleStopLimitMatchResult(Order order){
        return (order.getSide() == Side.BUY) ? MatchResult.notEnoughCredit() : MatchResult.notEnoughPositions();
    }

    private MatchResult processOrder(Order order , Matcher matcher){
        if(matchingState == MatchingState.CONTINUOUS){
            return (matcher.execute(order));
        }
        else {
            return (matcher.auctionAddToQueue(order));
        }
    }

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if(!checkPosition(enterOrderRq, shareholder)){
            return MatchResult.notEnoughPositions();
        }
        Order order = makeNewOrder(enterOrderRq, broker, shareholder);
        if (order instanceof StopLimitOrder){
            if (!checkOrderPossibility(order)){ 
                return impossibleStopLimitMatchResult(order);
            }
            if(!((StopLimitOrder)order).checkActivation(orderBook.getLastTradePrice())){
                return handleInactiveStopLimitOrder(order);
            }
        }
        return processOrder(order, matcher);
    }

    private Order findOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException{
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null) {
            order = orderBook.findInActiveByOrderId(deleteOrderRq.getSide() , deleteOrderRq.getOrderId());
            if (order == null){
                throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
            }
        }
        return order;
    }

    private void handleBuyOrderCredit(Order order){
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
    }

    private void decreaseBuyCredit(Order order){
        if (order.getSide() == Side.BUY ){
            order.getBroker().decreaseCreditBy(order.getValue());
        }
    }

    private void removeOrder(Order order, DeleteOrderRq deleteOrderRq){
        if (!orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId())){
            if (matchingState == MatchingState.CONTINUOUS){
                orderBook.removeInActiveStopLimitByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
            }
            else{
                decreaseBuyCredit(order);
            }
        }
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = findOrder(deleteOrderRq);
        handleBuyOrderCredit(order);
        removeOrder(order, deleteOrderRq); 
    }

    private Order getOrderForUpdate(EnterOrderRq upEnterOrderRq) throws InvalidRequestException{
        Order order;
        order = orderBook.findInActiveByOrderId(upEnterOrderRq.getSide(), upEnterOrderRq.getOrderId());
        if (order == null){
            order = orderBook.findByOrderId(upEnterOrderRq.getSide(), upEnterOrderRq.getOrderId());
            if (order == null) {
                throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
            }
        }
        return order;
    }

    private boolean validateUpdateOrder(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        ValidateRq validateRq = new ValidateRq(updateOrderRq, null, null, null);
        validateRq.validateUpdateOrderRq(order, updateOrderRq, orderBook);
        return !checkUpdateEnoughPosition(order, updateOrderRq);
    }
    
    private boolean isLosesPriority(Order order, EnterOrderRq updateOrderRq){
        return order.isQuantityIncreased(updateOrderRq.getQuantity())
        || updateOrderRq.getPrice() != order.getPrice()
        || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));
    }

    private boolean checkUpdateEnoughPosition(Order order, EnterOrderRq updateOrderRq){
        return updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                        orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity());
    }

    private MatchResult executeActiveOrder(Order order, Order originalOrder, EnterOrderRq updateOrderRq){
        if (!isLosesPriority(originalOrder, updateOrderRq) && updateOrderRq.getStopPrice() == 0) {
            decreaseBuyCredit(order);
            return MatchResult.executed(null, List.of()); 
        } else{
            order.markAsUpdating();
            return null;
        }
    }

    private MatchResult removePrevOrder(Order order, Order originalOrder, EnterOrderRq updateOrderRq, MatchResult matchResult){
        if(matchResult == null && updateOrderRq.getStopPrice() > 0){
            orderBook.removeInActiveStopLimitByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
            if(!((StopLimitOrder)order).checkActivation(orderBook.getLastTradePrice())){
                return handleInactiveStopLimitOrder(order);
            }
            return null;
        }
        else if (matchResult == null){
            orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
            return null;
        }
        else{
            return matchResult;
        }
    }

    private void enqueueUpdatedOrder(MatchResult matchResult, Order originalOrder){
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            orderBook.enqueueActiveStopLimitOrder(originalOrder);
            decreaseBuyCredit(originalOrder);
        }
    }
    
    private MatchResult execUpdatedOrder(MatchResult matchResult, Matcher matcher, Order order, Order originalOrder, EnterOrderRq updateOrderRq){
        if(matchResult == null && matchingState == MatchingState.CONTINUOUS){
            matchResult = matcher.execute(order);
            enqueueUpdatedOrder(matchResult, originalOrder);
            return matchResult;
        }
        else if (matchResult == null){
            matchResult = matcher.auctionAddToQueue(order);
            return matchResult;
        }
        else{
            return matchResult;
        }
    }

    private MatchResult processUpdatedOrder(Order order, Order originalOrder, EnterOrderRq updateOrderRq, Matcher matcher){
        MatchResult matchResult = executeActiveOrder(order, originalOrder, updateOrderRq);
        matchResult = removePrevOrder(order, originalOrder, updateOrderRq, matchResult);
        matchResult = execUpdatedOrder(matchResult, matcher, order, originalOrder, updateOrderRq);
        return matchResult;
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = getOrderForUpdate(updateOrderRq);
        if (validateUpdateOrder(order, updateOrderRq)) {
            handleBuyOrderCredit(order);
            Order originalOrder = order.snapshot();
            order.updateFromRequest(updateOrderRq);
            MatchResult matchResult = processUpdatedOrder(order, originalOrder, updateOrderRq, matcher);
            return matchResult;
        }
        else{
            return MatchResult.notEnoughPositions();
        }
    }

    public void processActivatedStopLimitOrders(Matcher matcher) {
        List<Order>activatedOrders = orderBook.getActiveStopLimitOrders();
        for (Order activatedOrder : activatedOrders) {    
            MatchResult matchResult = matcher.execute(activatedOrder);
            activatedOrders.remove(activatedOrder);
            matchResults.add(matchResult);
        }
    }

    public boolean checkOrderPossibility(Order order){
        if(order.getSide() == Side.BUY){
            return order.getBroker().hasEnoughCredit(order.getValue());
        }
        else{
            return order.getShareholder().hasEnoughPositionsOn(order.getSecurity(), order.getQuantity());
        }
    }
    
    private MatchResult handleInactiveStopLimitOrder(Order order){
        decreaseBuyCredit(order);
        orderBook.enqueueInactiveStopLimitOrder(order);
        return MatchResult.inactiveOrderEnqueued();
    }

    public MatchResult ChangeMatchStateRq(MatchingState state , Matcher matcher){
        updateIndicativeOpeningPrice();
        if (state == MatchingState.CONTINUOUS &&  matchingState == MatchingState.AUCTION){
            MatchResult matchResult = openingProcess(matcher);
            matchingState =  MatchingState.CONTINUOUS ;
            return matchResult;
        }
        else if (state == MatchingState.AUCTION &&  matchingState == MatchingState.AUCTION){
            MatchResult matchResult = openingProcess(matcher);
            matchingState =  MatchingState.AUCTION ;
            return matchResult ;

        }
        else if (state == MatchingState.AUCTION &&  matchingState == MatchingState.CONTINUOUS){
            matchingState =  MatchingState.AUCTION ;
            return null;
        }
        else {
            matchingState =  MatchingState.CONTINUOUS ;
            return null;
        }
    }

    private MatchResult openingProcess(Matcher matcher){
        updateIndicativeOpeningPrice();
        LinkedList<Trade> trades = new LinkedList<>();
        int max = orderBook.getSellQueue().size();
        for(int i = 0 ; i < max ; i++){
            MatchResult matchResult = matcher.auctionExecute(orderBook.getSellQueue().get(i), indicativeOpeningPrice);
            if (matchResult.trades().size() == 0){
                continue;
            }
            for (Trade trade : matchResult.trades() ){
                trades.add(trade);
            }
        }
        Iterator<Order> sellIterator = orderBook.getSellQueue().iterator();
        while (sellIterator.hasNext()) {
            Order sellOrder = sellIterator.next();
            if (sellOrder.getQuantity() == 0) {
                sellIterator.remove();
            }
        }
        
        Iterator<Order> buyIterator = orderBook.getBuyQueue().iterator();
        while (buyIterator.hasNext()) {
            Order buyOrder = buyIterator.next();
            if (buyOrder.getQuantity() == 0) {
                buyIterator.remove();
            }
        }
        MatchResult matchResult = MatchResult.traded(trades);
        return matchResult;
    }

    private int getTotalQuantityInOrderList(LinkedList <Order> orders){
        int  sumQuantity = 0;
        for (Order order : orders){
            sumQuantity += order.getTotalQuantity();
        }
        return sumQuantity;
    }

    private int findOverallQuantityTraded(int selectedOpenPrice ){
        LinkedList <Order> selectedBuyOrders = new LinkedList<>();
        for (Order order : orderBook.getBuyQueue()){
            if(order.getPrice() >= selectedOpenPrice){
                selectedBuyOrders.add(order);
            }
        }
        LinkedList <Order> selectedSellOrders = new LinkedList<>();
        for (Order order : orderBook.getSellQueue()){
            if(order.getPrice() <= selectedOpenPrice){
                selectedSellOrders.add(order);
            }
        }
        int sumQuantityInSellQueue = getTotalQuantityInOrderList(selectedSellOrders);
        int sumQuantityInBuyQueue = getTotalQuantityInOrderList(selectedBuyOrders);
        return Math.min(sumQuantityInSellQueue , sumQuantityInBuyQueue);
    }

    private int findClosestToLastTradePrice(LinkedList<Integer> openPrices ){
        int minDistance = Integer.MAX_VALUE;
        int minElement = Integer.MAX_VALUE;
        for (int price : openPrices){
            int distance = Math.abs(price - (int)orderBook.getLastTradePrice());
            if(distance < minDistance){
                minDistance = distance;
                minElement = price;
            }
            else if (distance == minDistance && price < minElement){
                minElement = price;
            }
        }
        return minElement;
    }

    public int findBestAuctionPrice(LinkedList <Integer> allOrdersPrices){
        if (allOrdersPrices.size() == 0){
            return 0;
        }
        System.out.println(allOrdersPrices);
        int min = Collections.min(allOrdersPrices);
        int max = Collections.max(allOrdersPrices);
        int maxQuantityTraded = 0;
        LinkedList<Integer> bestOpenPrices = new LinkedList<>();
        for ( int i = min ; i <= max ; i++){
            int overallQuantityTraded = findOverallQuantityTraded(i);
            if(overallQuantityTraded > maxQuantityTraded){
                maxQuantityTraded = overallQuantityTraded;
                bestOpenPrices.clear();
                bestOpenPrices.add(i);
            }
            else if (overallQuantityTraded == maxQuantityTraded && overallQuantityTraded != 0 ){
                bestOpenPrices.add(i);
            }
        }
        highestQuantity = maxQuantityTraded;
        return findClosestToLastTradePrice(bestOpenPrices );
    }

    public int updateIndicativeOpeningPrice( ){
        //if?
        LinkedList <Integer> allOrdersPrices = new LinkedList<>() ;

        for (Order buyOrder : orderBook.getBuyQueue()) {
            allOrdersPrices.add(buyOrder.getPrice());
        }
        for (Order sellOrder : orderBook.getSellQueue()) {
            allOrdersPrices.add(sellOrder.getPrice());
        }
        int bestAuctionPrice = findBestAuctionPrice(allOrdersPrices);
        if (bestAuctionPrice == Integer.MAX_VALUE){
            bestAuctionPrice = 0;
        }
        indicativeOpeningPrice = bestAuctionPrice;
        return bestAuctionPrice;
    }

    
}


