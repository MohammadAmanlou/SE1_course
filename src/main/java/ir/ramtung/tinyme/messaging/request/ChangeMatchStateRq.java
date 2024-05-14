package ir.ramtung.tinyme.messaging.request;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@NoArgsConstructor
public class ChangeMatchStateRq {

    private LocalDateTime time;
    private String securityIsin;
    private MatchingState state;

    private int price;
    private int quantity;
    private long buyId;
    private long sellId;


    private int openingPrice;
    private int tradableQuantity;
}
