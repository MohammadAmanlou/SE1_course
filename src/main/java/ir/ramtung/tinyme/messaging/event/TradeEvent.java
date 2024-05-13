package ir.ramtung.tinyme.messaging.event;

import java.time.LocalDateTime;

public class TradeEvent {
    private LocalDateTime time;
    private String securityIsin;
    private int price;
    private int quantity;
    private long buyId;
    private long sellId;
    
}
