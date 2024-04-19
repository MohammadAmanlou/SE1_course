package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class stopLimitOrder extends Order {
    int stopPrice ; 
    Boolean isActive ;

    public stopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int stopPrice, OrderStatus status) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status , 0);
        this.stopPrice = stopPrice;
        this.isActive = false ; 
    }

    public stopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int stopPrice) {
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, stopPrice, OrderStatus.NEW);
        this.isActive = false ; 
    }

    public stopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, int stopPrice, int minimumExecutionQuantity) {
        super(orderId, security, side, quantity, price, broker, shareholder, 0);
        this.stopPrice = stopPrice;
        this.isActive = false ; 
    }

    @Override
    public Order snapshot() {
        return new stopLimitOrder(orderId, security, side, quantity, price, broker, shareholder, entryTime, stopPrice, OrderStatus.SNAPSHOT);
    }

    @Override
    public Order snapshotWithQuantity(int newQuantity) {
        return new stopLimitOrder(orderId, security, side, newQuantity, price, broker, shareholder, entryTime, stopPrice, OrderStatus.SNAPSHOT);
    }

    // @Override
    // public void decreaseQuantity(int amount) {
    //     if (status == OrderStatus.NEW) {
    //         super.decreaseQuantity(amount);
    //         return;
    //     }
    //     if (amount > displayedQuantity)
    //         throw new IllegalArgumentException();
    //     quantity -= amount;
    //     displayedQuantity -= amount;
    // }

    // @Override
    // public void updateFromRequest(EnterOrderRq updateOrderRq) {
    //     super.updateFromRequest(updateOrderRq);
    //     if (peakSize < updateOrderRq.getPeakSize()) {
    //         displayedQuantity = Math.min(quantity, updateOrderRq.getPeakSize());
    //     }
    //     else if (peakSize > updateOrderRq.getPeakSize()) {
    //         displayedQuantity = Math.min(displayedQuantity, updateOrderRq.getPeakSize());
    //     }
    //     peakSize = updateOrderRq.getPeakSize();
    // }
}
