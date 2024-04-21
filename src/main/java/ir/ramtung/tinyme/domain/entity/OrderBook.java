package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Getter
public class OrderBook {
    private final LinkedList<Order> buyQueue;
    private final LinkedList<Order> sellQueue;
    private LinkedList<Order> activeStopLimitOrders;
    private LinkedList<Order> inactiveStopLimitOrders;

    private double lastTradePrice;

    public OrderBook() {
        buyQueue = new LinkedList<>();
        sellQueue = new LinkedList<>();
        activeStopLimitOrders = new LinkedList<>();
        inactiveStopLimitOrders = new LinkedList<>();

    }

    public void enqueue(Order order) {
        List<Order> queue = getQueue(order.getSide());
        ListIterator<Order> it = queue.listIterator();
        while (it.hasNext()) {
            if (order.queuesBefore(it.next())) {
                it.previous();
                break;
            }
        }
        order.queue();
        it.add(order);
    }

    private LinkedList<Order> getQueue(Side side) {
        return side == Side.BUY ? buyQueue : sellQueue;
    }

    public Order findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        for (Order order : queue) {
            if (order.getOrderId() == orderId)
                return order;
        }
        return null;
    }

    public boolean removeByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        var it = queue.listIterator();
        while (it.hasNext()) {
            if (it.next().getOrderId() == orderId) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public Order matchWithFirst(Order newOrder) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }

    public void putBack(Order order) {
        LinkedList<Order> queue = getQueue(order.getSide());
        order.queue();
        queue.addFirst(order);
    }

    public void restoreSellOrder(Order sellOrder) {
        removeByOrderId(Side.SELL, sellOrder.getOrderId());
        putBack(sellOrder);
    }
    public void restoreBuyOrder(Order buyOrder) {
        removeByOrderId(Side.BUY, buyOrder.getOrderId());
        putBack(buyOrder);
    }

    public boolean hasOrderOfType(Side side) {
        return !getQueue(side).isEmpty();
    }

    public void removeFirst(Side side) {
        getQueue(side).removeFirst();
    }

    public int totalSellQuantityByShareholder(Shareholder shareholder) {
        return sellQueue.stream()
                .filter(order -> order.getShareholder().equals(shareholder))
                .mapToInt(Order::getTotalQuantity)
                .sum();
    }

    public void setLastTradePrice(double lastTradePrice) {
        this.lastTradePrice = lastTradePrice;
    }

    public List<Order> activateStopLimitOrders() {

        List<Order> activatedOrders = new ArrayList<>();

        for (ListIterator<Order> it = inactiveStopLimitOrders.listIterator(); it.hasNext(); ) {
            Order order = it.next();
            if (order.getStopPrice() <= lastTradePrice && order.getSide() == Side.SELL ||
                    order.getStopPrice() >= lastTradePrice && order.getSide() == Side.BUY) {
                it.remove();
                activeStopLimitOrders.add(order);
                activatedOrders.add(order);
            }
        }

        return activatedOrders;
    }
}
