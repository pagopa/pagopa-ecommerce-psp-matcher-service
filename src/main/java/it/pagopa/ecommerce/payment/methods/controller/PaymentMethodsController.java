package it.pagopa.ecommerce.payment.methods.controller;

import it.pagopa.ecommerce.payment.methods.application.PaymentMethodService;
import it.pagopa.ecommerce.payment.methods.domain.aggregates.PaymentMethod;
import it.pagopa.ecommerce.payment.methods.exception.AfmResponseException;
import it.pagopa.ecommerce.payment.methods.exception.PaymentMethodAlreadyInUseException;
import it.pagopa.ecommerce.payment.methods.exception.PaymentMethodNotFoundException;
import it.pagopa.ecommerce.payment.methods.server.api.PaymentMethodsApi;
import it.pagopa.ecommerce.payment.methods.server.model.*;
import it.pagopa.ecommerce.payment.methods.utils.PaymentMethodStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@RestController
@Slf4j
public class PaymentMethodsController implements PaymentMethodsApi {

    @Autowired
    private PaymentMethodService paymentMethodService;

    @ExceptionHandler(
        {
                PaymentMethodAlreadyInUseException.class,
                PaymentMethodNotFoundException.class,
        }
    )
    public ResponseEntity<ProblemJsonDto> errorHandler(RuntimeException exception) {
        if (exception instanceof PaymentMethodAlreadyInUseException) {
            return new ResponseEntity<>(
                    new ProblemJsonDto().status(404).title("Bad request").detail("Payment method already in use"),
                    HttpStatus.BAD_REQUEST
            );
        } else if (exception instanceof PaymentMethodNotFoundException) {
            return new ResponseEntity<>(
                    new ProblemJsonDto().status(404).title("Not found").detail("Payment method not found"),
                    HttpStatus.NOT_FOUND
            );
        } else if (exception instanceof AfmResponseException) {
            return new ResponseEntity<>(
                    new ProblemJsonDto().status(((AfmResponseException) exception).status.value())
                            .title("Afm generic error")
                            .detail(((AfmResponseException) exception).reason),
                    ((AfmResponseException) exception).status
            );
        } else {
            return new ResponseEntity<>(
                    new ProblemJsonDto().status(500).title("Internal server error"),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Override
    public Mono<ResponseEntity<Flux<PaymentMethodResponseDto>>> getAllPaymentMethods(
                                                                                     BigDecimal amount,
                                                                                     ServerWebExchange exchange
    ) {
        return Mono.just(
                ResponseEntity.ok(
                        paymentMethodService.retrievePaymentMethods(amount != null ? amount.intValue() : null)
                                .map(paymentMethod -> {
                                    PaymentMethodResponseDto responseDto = new PaymentMethodResponseDto();
                                    responseDto.setId(paymentMethod.getPaymentMethodID().value().toString());
                                    responseDto.setName(paymentMethod.getPaymentMethodName().value());
                                    responseDto.setDescription(paymentMethod.getPaymentMethodDescription().value());
                                    responseDto.setStatus(
                                            PaymentMethodResponseDto.StatusEnum
                                                    .valueOf(paymentMethod.getPaymentMethodStatus().value().toString())
                                    );
                                    responseDto.setRanges(
                                            paymentMethod.getPaymentMethodRanges().stream().map(
                                                    r -> {
                                                        RangeDto rangeDto = new RangeDto();
                                                        rangeDto.setMin(r.min());
                                                        rangeDto.setMax(r.max());
                                                        return rangeDto;
                                                    }
                                            ).collect(Collectors.toList())
                                    );
                                    responseDto.setPaymentTypeCode(paymentMethod.getPaymentMethodTypeCode().value());
                                    responseDto.setAsset(paymentMethod.getPaymentMethodAsset().value());

                                    return responseDto;
                                })
                )
        );
    }

    @Override
    public Mono<ResponseEntity<PaymentMethodResponseDto>> getPaymentMethod(
                                                                           String id,
                                                                           ServerWebExchange exchange
    ) {
        return paymentMethodService.retrievePaymentMethodById(id)
                .map(this::paymentMethodToResponse);
    }

    @Override
    public Mono<ResponseEntity<PaymentMethodResponseDto>> newPaymentMethod(
                                                                           @Valid Mono<PaymentMethodRequestDto> paymentMethodRequestDto,
                                                                           ServerWebExchange exchange
    ) {
        return paymentMethodRequestDto.flatMap(
                request -> paymentMethodService.createPaymentMethod(
                        request.getName(),
                        request.getDescription(),
                        request.getRanges().stream()
                                .map(r -> Pair.of(r.getMin(), r.getMax()))
                                .toList(),
                        request.getPaymentTypeCode(),
                        request.getAsset()
                )
                        .map(this::paymentMethodToResponse)
        );
    }

    @Override
    public Mono<ResponseEntity<PaymentMethodResponseDto>> patchPaymentMethod(
                                                                             String id,
                                                                             Mono<PatchPaymentMethodRequestDto> patchPaymentMethodRequestDto,
                                                                             ServerWebExchange exchange
    ) {
        return patchPaymentMethodRequestDto
                .flatMap(
                        request -> paymentMethodService
                                .updatePaymentMethodStatus(
                                        id,
                                        PaymentMethodStatusEnum.valueOf(request.getStatus().toString())
                                )
                                .map(this::paymentMethodToResponse)
                );
    }

    private ResponseEntity<PaymentMethodResponseDto> paymentMethodToResponse(PaymentMethod paymentMethod) {
        PaymentMethodResponseDto response = new PaymentMethodResponseDto();
        response.setId(paymentMethod.getPaymentMethodID().value().toString());
        response.setName(paymentMethod.getPaymentMethodName().value());
        response.setDescription(paymentMethod.getPaymentMethodDescription().value());
        response.setStatus(
                PaymentMethodResponseDto.StatusEnum.valueOf(
                        paymentMethod.getPaymentMethodStatus().value().toString()
                )
        );
        response.setRanges(
                paymentMethod.getPaymentMethodRanges().stream().map(
                        r -> {
                            RangeDto rangeDto = new RangeDto();
                            rangeDto.setMin(r.min());
                            rangeDto.setMax(r.max());
                            return rangeDto;
                        }
                ).collect(Collectors.toList())
        );
        response.setPaymentTypeCode(paymentMethod.getPaymentMethodTypeCode().value());
        response.setAsset(paymentMethod.getPaymentMethodAsset().value());
        return ResponseEntity.ok(response);
    }

    @Override
    public Mono<ResponseEntity<BundleOptionDto>> calculateFees(
                                                               String id,
                                                               Mono<PaymentOptionDto> paymentOptionDto,
                                                               Integer maxOccurrences,
                                                               ServerWebExchange exchange
    ) {

        return paymentMethodService.computeFee(paymentOptionDto, id, maxOccurrences).map(
                ResponseEntity::ok
        );
    }
}
