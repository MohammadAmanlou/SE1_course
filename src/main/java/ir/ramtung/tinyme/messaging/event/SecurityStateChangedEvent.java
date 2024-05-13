package ir.ramtung.tinyme.messaging.event;

import java.time.LocalDateTime;
import ir.ramtung.tinyme.messaging.request.MatchingState;
public class SecurityStateChangedEvent {
    private LocalDateTime time;
    private String securityIsin;
    private MatchingState state;
}
