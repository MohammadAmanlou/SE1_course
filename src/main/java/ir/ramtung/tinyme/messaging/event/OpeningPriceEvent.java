package ir.ramtung.tinyme.messaging.event;

import java.time.LocalDateTime;

public class OpeningPriceEvent {
    private LocalDateTime time;
    private String securityIsin;
    private int openingPrice;
    private int tradableQuantity;
}
