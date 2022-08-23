package it.pagopa.ecommerce.payment.instruments.controller;

import it.pagopa.ecommerce.payment.instruments.application.PaymentMethodService;
import it.pagopa.ecommerce.payment.instruments.application.PspService;
import it.pagopa.ecommerce.payment.instruments.client.ApiConfigClient;
import it.pagopa.ecommerce.payment.instruments.domain.aggregates.PaymentMethod;
import it.pagopa.ecommerce.payment.instruments.server.api.PaymentMethodsApi;
import it.pagopa.ecommerce.payment.instruments.server.model.*;
import it.pagopa.ecommerce.payment.instruments.utils.PaymentMethodStatusEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.stream.Collectors;

@RestController
public class PaymentInstrumentsController implements PaymentMethodsApi {

    @Autowired
    private PaymentMethodService paymentMethodService;

    @Autowired
    private PspService pspService;

    @Autowired
    private ApiConfigClient apiConfigClient;

    @Override
    public Mono<ResponseEntity<Flux<PaymentMethodResponseDto>>> getAllPaymentMethods(ServerWebExchange exchange) {
        // TODO: add amount filter
        return Mono.just(ResponseEntity.ok(paymentMethodService.retrievePaymentMethods(null)
                .map(paymentMethod -> {
                    PaymentMethodResponseDto responseDto = new PaymentMethodResponseDto();
                    responseDto.setId(paymentMethod.getPaymentMethodID().value().toString());
                    responseDto.setDescription(paymentMethod.getPaymentMethodDescription().value());
                    responseDto.setStatus(PaymentMethodResponseDto.StatusEnum.valueOf(paymentMethod.getPaymentMethodStatus().value().toString()));
                    responseDto.setRanges(paymentMethod.getPaymentMethodRanges().stream().map(
                            r -> {
                                RangeDto rangeDto = new RangeDto();
                                rangeDto.setMin(r.min());
                                rangeDto.setMax(r.max());
                                return rangeDto;
                            }
                    ).collect(Collectors.toList()));
                    responseDto.setPaymentTypeCode(paymentMethod.getPaymentMethodTypeCode().value());

                    return responseDto;
                })
        ));
    }

    @Override
    public Mono<ResponseEntity<PSPsResponseDto>> getPSPs(Integer amount, String lang, String paymentTypeCode, ServerWebExchange exchange) {
        return pspService.retrievePsps(amount, lang, paymentTypeCode).collectList().flatMap(
                pspDtos -> {
                    PSPsResponseDto responseDto = new PSPsResponseDto();
                    responseDto.setPsp(pspDtos);

                    return Mono.just(ResponseEntity.ok(responseDto));
                }
        );
    }

    @Override
    public Mono<ResponseEntity<PaymentMethodResponseDto>> getPaymentMethod(String id, ServerWebExchange exchange) {
        return paymentMethodService.retrievePaymentMethodById(id)
                .map(this::paymentMethodToResponse);
    }

    @Override
    public Mono<ResponseEntity<PSPsResponseDto>> getPaymentMethodsPSPs(String id, Integer amount, String lang, ServerWebExchange exchange) {
        return null;
    }

    @Override
    public Mono<ResponseEntity<PaymentMethodResponseDto>> newPaymentMethod(@Valid Mono<PaymentMethodRequestDto> paymentMethodRequestDto,
                                                                           ServerWebExchange exchange) {
        return paymentMethodRequestDto.flatMap(request -> paymentMethodService.createPaymentMethod(
                request.getName(),
                request.getDescription(),
                request.getRanges().stream().map(r -> Pair.of(r.getMin(), r.getMax())).collect(Collectors.toList()),
                request.getPaymentTypeCode())
                .map(this::paymentMethodToResponse)
        );
    }

    @Override
    public Mono<ResponseEntity<PaymentMethodResponseDto>> patchPaymentMethod(String id, Mono<PatchPaymentMethodRequestDto> patchPaymentMethodRequestDto, ServerWebExchange exchange) {
        return patchPaymentMethodRequestDto
                .flatMap(request -> paymentMethodService
                        .updatePaymentMethodStatus(id, PaymentMethodStatusEnum.valueOf(request.getStatus().toString()))
                        .map(this::paymentMethodToResponse));
    }

    @Override
    public Mono<ResponseEntity<Void>> scheduleUpdatePSPs(ServerWebExchange exchange) {
        return null;
    }

    private ResponseEntity<PaymentMethodResponseDto> paymentMethodToResponse(PaymentMethod paymentMethod){
        PaymentMethodResponseDto response = new PaymentMethodResponseDto();
        response.setId(paymentMethod.getPaymentMethodID().value().toString());
        response.setName(paymentMethod.getPaymentMethodName().value());
        response.setDescription(paymentMethod.getPaymentMethodDescription().value());
        response.setStatus(PaymentMethodResponseDto.StatusEnum.valueOf(
                paymentMethod.getPaymentMethodStatus().value().toString()));
        response.setRanges(paymentMethod.getPaymentMethodRanges().stream().map(
                r -> {
                    RangeDto rangeDto = new RangeDto();
                    rangeDto.setMin(r.min());
                    rangeDto.setMax(r.max());
                    return rangeDto;
                }
        ).collect(Collectors.toList()));
        response.setPaymentTypeCode(paymentMethod.getPaymentMethodTypeCode().value());
        return ResponseEntity.ok(response);
    }
}
