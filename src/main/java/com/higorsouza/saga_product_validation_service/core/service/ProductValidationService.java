package com.higorsouza.saga_product_validation_service.core.service;

import com.higorsouza.saga_product_validation_service.config.exception.ValidationException;
import com.higorsouza.saga_product_validation_service.core.dto.Event;
import com.higorsouza.saga_product_validation_service.core.dto.History;
import com.higorsouza.saga_product_validation_service.core.dto.OrderProducts;
import com.higorsouza.saga_product_validation_service.core.enums.ESagaStatus;
import com.higorsouza.saga_product_validation_service.core.model.Validation;
import com.higorsouza.saga_product_validation_service.core.producer.KafkaProducer;
import com.higorsouza.saga_product_validation_service.core.repository.ProductRepository;
import com.higorsouza.saga_product_validation_service.core.repository.ValidationRepository;
import com.higorsouza.saga_product_validation_service.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.springframework.util.StringUtils.isEmpty;


@Log4j2
@AllArgsConstructor
@Service
public class ProductValidationService {

    private static final String CURRENT_SOURCE = "PRODUCT_VALIDATION_SERVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer kafkaProducer;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ValidationRepository validationRepository;

    public void validateExistingProducts(Event event) {
        log.info("Service validateExistingProducts {}", event);

        try {
            checkCurrentValidation(event);
            createValidation(event, true);
            handleSuccess(event);
        } catch (Exception ex) {
            log.error("Error trying to validate products: ", ex);
            handleFailCurrentNotExecuted(event, ex.getMessage());
        }
        kafkaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void validateProductsInformed(Event event) {
        if (isEmpty(event.getPayload()) || isEmpty(event.getPayload().getProducts())){
            throw new ValidationException("Product list is empty!");
        }
        if (isEmpty(event.getPayload().getId()) || isEmpty(event.getPayload().getTransactionId())){
            throw new ValidationException("OrderID and TransactionID must be informed!");
        }
    }

    private void checkCurrentValidation(Event event) {
        validateProductsInformed(event);
        if (validationRepository.existsByOrderIdAndTransactionId(
                event.getOrderId(), event.getTransactionId())){
            throw  new ValidationException("There's another transactionId for this validation.");
        }
        event.getPayload().getProducts().forEach(product -> {
            validateProductInformed(product);
            validateExistingProduct(product.getProduct().getCode());
        });
    }

    private void validateProductInformed(OrderProducts orderProducts){
        if(isEmpty(orderProducts.getProduct()) || isEmpty(orderProducts.getProduct().getCode())){
            throw new ValidationException("Product must be informed!");
        }
    }

    private void validateExistingProduct(String code){
        if(!productRepository.existsByCode(code)) {
            throw new ValidationException("Product does not exists in database!");
        }
    }

    private void createValidation(Event event, boolean success){
        var validation = Validation
                .builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .success(success)
                .build();
        validationRepository.save(validation);
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Products are validated successfully!");
    }

    private void addHistory(Event event, String message){
        var history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        event.addToHistory(history);
    }

    private void handleFailCurrentNotExecuted(Event event, String message) {
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to validate products: ".concat(message));
    }

    public void rollbackEvent(Event event){
        changeValidationToFail(event);
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Rollback executed on product validation!");
        kafkaProducer.sendEvent(jsonUtil.toJson(event));
    }

    private void changeValidationToFail(Event event){
        validationRepository.findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .ifPresentOrElse(validation -> {
                    validation.setSuccess(false);
                    validationRepository.save(validation);
                },
                        () -> createValidation(event, false));
    }

}
