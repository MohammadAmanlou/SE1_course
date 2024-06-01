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
import ir.ramtung.tinyme.messaging.request.Request;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;


import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Service
public class ValidateRq {
    EnterOrderRq request;
    List<String> errors;
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;

    public ValidateRq(EnterOrderRq request,SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) {
        this.request = request;
        this.errors = new LinkedList<>();
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
    }

    private boolean checkSecurityExistence(Security security){
        if (security == null){
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
            return false;
        }
        return true;
    }

    private void checkMultiple(EnterOrderRq enterOrderRq , Security security){
        if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
        errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
        if (enterOrderRq.getPrice() % security.getTickSize() != 0)
            errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
    }

    private void checkAuctionMEQ(EnterOrderRq enterOrderRq){
        if(enterOrderRq.getMinimumExecutionQuantity() > 0){
            errors.add(Message.MEQ_IS_PROHIBITED_IN_AUCTION_MODE);
        }
    }

    private void checkAuctionStopLimit(EnterOrderRq enterOrderRq){
        if(enterOrderRq.getStopPrice() > 0){
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER){
                errors.add(Message.STOPLIMIT_ORDER_IN_AUCTION_MODE_ERROR);
            }
            else{
                errors.add(Message.STOPLIMIT_ORDER_IN_AUCTION_MODE_CANT_UPDATE);
            }
        }
    }

    private void checkAuction(EnterOrderRq enterOrderRq , Security security){
            checkAuctionMEQ(enterOrderRq);
            checkAuctionStopLimit(enterOrderRq);
    }

    private void validateSecurity(EnterOrderRq enterOrderRq ){
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (checkSecurityExistence(security))
        {
            checkMultiple(enterOrderRq , security);
            if(security.getMatchingState() == MatchingState.AUCTION){
                checkAuction(enterOrderRq , security);
            }
        }
    }

    private void validateBroker(EnterOrderRq enterOrderRq ){
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
                errors.add(Message.UNKNOWN_BROKER_ID);
    }

    private void validateShareholder(EnterOrderRq enterOrderRq ){
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
                errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
    }

    public void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        try {
            validateOrder(enterOrderRq);
            validateSecurity(enterOrderRq);
            validateBroker(enterOrderRq);
            validateShareholder(enterOrderRq);
            if (!errors.isEmpty())
                throw new InvalidRequestException(errors);
        }
        catch(InvalidRequestException ex){
            throw ex;
        }
    }

    private void checkPositivity(EnterOrderRq enterOrderRq){
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
        if (enterOrderRq.getMinimumExecutionQuantity() < 0 )
            errors.add(Message.MINIMUM_EXECUTION_QUANTITY_IS_NEGATIVE);
    }
    
    private void checkMEQLessThanQuantity(EnterOrderRq enterOrderRq){
        if (enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity() )
            errors.add(Message.MINIMUM_EXECUTION_QUANTITY_IS_MORE_THAN_QUANTITY);
    }

    private void checkStopLimitNotIceberg(EnterOrderRq enterOrderRq){
        if ((enterOrderRq.getStopPrice() != 0) &&  (enterOrderRq.getPeakSize() != 0))
            errors.add(Message.STOP_LIMIT_ORDER_CANT_BE_ICEBERG);
    }

    private void checkPeakSize(EnterOrderRq enterOrderRq){
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
    }

    private void checkStopLimitZeroMEQ(EnterOrderRq enterOrderRq){
        if ((enterOrderRq.getStopPrice() != 0) &&  (enterOrderRq.getMinimumExecutionQuantity() != 0))
            errors.add(Message.STOP_LIMIT_ORDER_CANT_MEQ);
    }

    private void validateOrder(EnterOrderRq enterOrderRq){
        checkPositivity(enterOrderRq);
        checkMEQLessThanQuantity(enterOrderRq);
        checkStopLimitNotIceberg(enterOrderRq);
        checkStopLimitZeroMEQ(enterOrderRq);
        checkPeakSize(enterOrderRq);
    }

}
