package ir.ramtung.tinyme.domain.service.validations;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;

import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;


@Service
public class ValidateRq {
    EnterOrderRq request;
    List<String> errors;
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;

    public ValidateRq(EnterOrderRq request, SecurityRepository securityRepository, BrokerRepository brokerRepository, ShareholderRepository shareholderRepository) {
        this.request = request;
        this.errors = new LinkedList<>();
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
    }

    private ValidationHandler createValidationChain() {
        ValidationHandler checkPositivityHandler = new CheckPositivityHandler();
        ValidationHandler checkMEQLessThanQuantityHandler = new CheckMEQLessThanQuantityHandler();
        ValidationHandler checkStopLimitNotIcebergHandler = new CheckStopLimitNotIcebergHandler();
        ValidationHandler checkPeakSizeHandler = new CheckPeakSizeHandler();
        ValidationHandler checkStopLimitZeroMEQHandler = new CheckStopLimitZeroMEQHandler();
        ValidationHandler validateSecurityHandler = new ValidateSecurityHandler(securityRepository);
        ValidationHandler validateBrokerHandler = new ValidateBrokerHandler(brokerRepository);
        ValidationHandler validateShareholderHandler = new ValidateShareholderHandler(shareholderRepository);

        checkPositivityHandler.setNext(checkMEQLessThanQuantityHandler);
        checkMEQLessThanQuantityHandler.setNext(checkStopLimitNotIcebergHandler);
        checkStopLimitNotIcebergHandler.setNext(checkPeakSizeHandler);
        checkPeakSizeHandler.setNext(checkStopLimitZeroMEQHandler);
        checkStopLimitZeroMEQHandler.setNext(validateSecurityHandler);
        validateSecurityHandler.setNext(validateBrokerHandler);
        validateBrokerHandler.setNext(validateShareholderHandler);

        return checkPositivityHandler; // Return the first handler in the chain
    }

    public void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        try {
            ValidationHandler validationChain = createValidationChain();
            validationChain.handle(enterOrderRq, errors);

            if (!errors.isEmpty()) {
                throw new InvalidRequestException(errors);
            }
        } catch (InvalidRequestException ex) {
            throw ex;
        }
    }

    public void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private void validateInvalidUpdatePeakSize(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
    }

    private void validateNonIcebergHavingPeakSize(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
    }

    private void validatePeakSize(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        validateInvalidUpdatePeakSize(order, updateOrderRq);
        validateNonIcebergHavingPeakSize(order, updateOrderRq);
    }

    private void validateUpdateActiveStopLimit(Order order, OrderBook orderBook, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        if ((order instanceof StopLimitOrder) && (orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId()) != null)){ 
            throw new InvalidRequestException(Message.UPDATING_REJECTED_BECAUSE_THE_STOP_LIMIT_ORDER_IS_ACTIVE);
        }
    }

    private void validateUpdateStopPriceForNonStopLimit(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        if (!(order instanceof StopLimitOrder) && updateOrderRq.getStopPrice() > 0){
            throw new InvalidRequestException(Message.UPDATING_REJECTED_BECAUSE_IT_IS_NOT_STOP_LIMIT_ORDER);
        }
    }

    private void validateZeroStopPriceForStopLimit(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        if ((order instanceof StopLimitOrder) && updateOrderRq.getStopPrice() == 0){
            throw new InvalidRequestException(Message.UPDATING_REJECTED_BECAUSE_IT_IS_NOT_STOP_LIMIT_ORDER);
        }
    }

    private void validateStopLimitHaveMEQ(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        if ((order instanceof StopLimitOrder) && (updateOrderRq.getMinimumExecutionQuantity() != 0) && (order.getMinimumExecutionQuantity() == 0)){
            throw new InvalidRequestException(Message.STOP_LIMIT_ORDER_CANT_MEQ);
        }
    }

    private void validateStopLimitBeIceberg(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        if ((order instanceof StopLimitOrder) && (updateOrderRq.getPeakSize() != 0) ){
            throw new InvalidRequestException(Message.STOP_LIMIT_ORDER_CANT_BE_ICEBERG);
        }
    }

    private void validateUpdateStopLimit(Order order, OrderBook orderBook, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        validateUpdateActiveStopLimit(order, orderBook, updateOrderRq);
        validateUpdateStopPriceForNonStopLimit(order, updateOrderRq);
        validateZeroStopPriceForStopLimit(order, updateOrderRq);
        validateStopLimitHaveMEQ(order, updateOrderRq);
        validateStopLimitBeIceberg(order, updateOrderRq);
    }

    private void validateUpdateMEQ(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException{
        if (order.getMinimumExecutionQuantity() != updateOrderRq.getMinimumExecutionQuantity())
            throw new InvalidRequestException(Message.CAN_NOT_UPDATE_ORDER_MINIMUM_EXECUTION_QUANTITY);
    }

    public void validateUpdateOrderRq(Order order, EnterOrderRq updateOrderRq, OrderBook orderBook) throws InvalidRequestException{
        validatePeakSize(order, updateOrderRq);
        validateUpdateStopLimit(order, orderBook, updateOrderRq);
        validateUpdateMEQ(order, updateOrderRq);
    }


}
