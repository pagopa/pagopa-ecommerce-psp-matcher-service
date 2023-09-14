package it.pagopa.ecommerce.payment.methods.application;

import it.pagopa.ecommerce.commons.client.NpgClient;
import it.pagopa.ecommerce.commons.domain.v1.TransactionId;
import it.pagopa.ecommerce.commons.generated.npg.v1.dto.FieldsDto;
import it.pagopa.ecommerce.payment.methods.client.AfmClient;
import it.pagopa.ecommerce.payment.methods.config.SessionUrlConfig;
import it.pagopa.ecommerce.payment.methods.domain.aggregates.PaymentMethod;
import it.pagopa.ecommerce.payment.methods.domain.aggregates.PaymentMethodFactory;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.*;
import it.pagopa.ecommerce.payment.methods.exception.*;
import it.pagopa.ecommerce.payment.methods.infrastructure.*;
import it.pagopa.ecommerce.payment.methods.server.model.*;
import it.pagopa.ecommerce.payment.methods.utils.ApplicationService;
import it.pagopa.ecommerce.payment.methods.utils.PaymentMethodStatusEnum;
import it.pagopa.generated.ecommerce.gec.v1.dto.PspSearchCriteriaDto;
import it.pagopa.generated.ecommerce.gec.v1.dto.TransferListItemDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ApplicationService
@Slf4j
public class PaymentMethodService {

    public enum SessionPaymentMethod {
        CARDS("CARDS");

        public final String value;

        SessionPaymentMethod(String value) {
            this.value = value;
        }

        public static SessionPaymentMethod fromValue(String value) {
            for (SessionPaymentMethod method : SessionPaymentMethod.values()) {
                if (method.value.equals(value)) {
                    return method;
                }
            }

            throw new IllegalArgumentException("Invalid session payment method: '%s'".formatted(value));
        }
    }

    private final AfmClient afmClient;

    private final NpgClient npgClient;

    private final PaymentMethodRepository paymentMethodRepository;

    private final PaymentMethodFactory paymentMethodFactory;

    private final SessionUrlConfig sessionUrlConfig;

    private final NpgSessionsTemplateWrapper npgSessionsTemplateWrapper;

    private final String npgDefaultApiKey;

    @Autowired
    public PaymentMethodService(
            AfmClient afmClient,
            PaymentMethodRepository paymentMethodRepository,
            PaymentMethodFactory paymentMethodFactory,
            NpgClient npgClient,
            SessionUrlConfig sessionUrlConfig,
            NpgSessionsTemplateWrapper npgSessionsTemplateWrapper,
            @Value("${npg.client.apiKey}") String npgDefaultApiKey
    ) {
        this.afmClient = afmClient;
        this.npgClient = npgClient;
        this.paymentMethodFactory = paymentMethodFactory;
        this.paymentMethodRepository = paymentMethodRepository;
        this.sessionUrlConfig = sessionUrlConfig;
        this.npgSessionsTemplateWrapper = npgSessionsTemplateWrapper;
        this.npgDefaultApiKey = npgDefaultApiKey;
    }

    public Mono<PaymentMethod> createPaymentMethod(
                                                   String paymentMethodName,
                                                   String paymentMethodDescription,
                                                   List<Pair<Long, Long>> ranges,
                                                   String paymentMethodTypeCode,
                                                   String paymentMethodAsset
    ) {
        log.info("[Payment Method Aggregate] Create new aggregate");
        Mono<PaymentMethod> paymentMethod = paymentMethodFactory.newPaymentMethod(
                new PaymentMethodID(UUID.randomUUID()),
                new PaymentMethodName(paymentMethodName),
                new PaymentMethodDescription(paymentMethodDescription),
                new PaymentMethodStatus(PaymentMethodStatusEnum.ENABLED),
                ranges.stream().map(pair -> new PaymentMethodRange(pair.getFirst(), pair.getSecond())).toList(),
                new PaymentMethodType(paymentMethodTypeCode),
                new PaymentMethodAsset(paymentMethodAsset),
                NpgClient.PaymentMethod.fromServiceName(paymentMethodName)
        );

        log.info("[Payment Method Aggregate] Store new aggregate");

        return paymentMethod.flatMap(
                p -> paymentMethodRepository.save(
                        new PaymentMethodDocument(
                                p.getPaymentMethodID().value().toString(),
                                p.getPaymentMethodName().value(),
                                p.getPaymentMethodDescription().value(),
                                p.getPaymentMethodStatus().value().toString(),
                                p.getPaymentMethodAsset().value(),
                                p.getPaymentMethodRanges().stream().map(r -> Pair.of(r.min(), r.max()))
                                        .toList(),
                                p.getPaymentMethodTypeCode().value()
                        )
                ).map(
                        doc -> new PaymentMethod(
                                new PaymentMethodID(UUID.fromString(doc.getPaymentMethodID())),
                                new PaymentMethodName(doc.getPaymentMethodName()),
                                new PaymentMethodDescription(doc.getPaymentMethodDescription()),
                                new PaymentMethodStatus(PaymentMethodStatusEnum.valueOf(doc.getPaymentMethodStatus())),
                                new PaymentMethodType(doc.getPaymentMethodTypeCode()),
                                doc.getPaymentMethodRanges().stream()
                                        .map(pair -> new PaymentMethodRange(pair.getFirst(), pair.getSecond()))
                                        .toList(),
                                new PaymentMethodAsset(doc.getPaymentMethodAsset()),
                                NpgClient.PaymentMethod.fromServiceName(doc.getPaymentMethodName())
                        )
                )
        );
    }

    public Flux<PaymentMethod> retrievePaymentMethods(Integer amount) {
        log.info("[Payment Method Aggregate] Retrieve Aggregate");

        if (amount == null) {
            return paymentMethodRepository.findAll().map(this::docToAggregate);
        } else {
            return paymentMethodRepository
                    .findAll()
                    .filter(
                            doc -> doc.getPaymentMethodRanges().stream()
                                    .anyMatch(
                                            range -> range.getFirst().longValue() <= amount
                                                    && range.getSecond().longValue() >= amount
                                    )
                    ).map(this::docToAggregate);
        }
    }

    public Mono<PaymentMethod> updatePaymentMethodStatus(
                                                         String id,
                                                         PaymentMethodStatusEnum status
    ) {
        log.info("[Payment method Aggregate] Patch Aggregate");

        return paymentMethodRepository
                .findById(id)
                .map(this::docToAggregate)
                .switchIfEmpty(
                        Mono.error(new PaymentMethodNotFoundException(id))
                )
                .map(p -> {
                    p.setPaymentMethodStatus(status);
                    return p;
                })
                .flatMap(
                        p -> paymentMethodRepository
                                .save(
                                        new PaymentMethodDocument(
                                                p.getPaymentMethodID().value().toString(),
                                                p.getPaymentMethodName().value(),
                                                p.getPaymentMethodDescription().value(),
                                                p.getPaymentMethodStatus().value().toString(),
                                                p.getPaymentMethodAsset().value(),
                                                p.getPaymentMethodRanges().stream().map(
                                                        r -> Pair.of(r.min(), r.max())
                                                ).toList(),
                                                p.getPaymentMethodTypeCode().value()
                                        )
                                )
                )
                .map(this::docToAggregate);
    }

    public Mono<PaymentMethod> retrievePaymentMethodById(String id) {
        log.info("[Payment Method Aggregate] Retrieve Aggregate");

        return paymentMethodRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new PaymentMethodNotFoundException(id)))
                .map(this::docToAggregate);
    }

    public Mono<CalculateFeeResponseDto> computeFee(
                                                    Mono<CalculateFeeRequestDto> paymentOptionDto,
                                                    String paymentMethodId,
                                                    Integer maxOccurrences
    ) {
        log.info("[Payment Method] Retrieve bundles list");
        return paymentMethodRepository.findById(paymentMethodId)
                .switchIfEmpty(Mono.error(new PaymentMethodNotFoundException(paymentMethodId)))
                .flatMap(
                        pm -> paymentOptionDto.map(
                                po -> Tuples.of(
                                        new it.pagopa.generated.ecommerce.gec.v1.dto.PaymentOptionDto()
                                                .bin(po.getBin())
                                                .paymentAmount(po.getPaymentAmount())
                                                .idPspList(
                                                        Optional.ofNullable(po.getIdPspList()).orElseGet(ArrayList::new)
                                                                .stream()
                                                                .map(idPsp -> new PspSearchCriteriaDto().idPsp(idPsp))
                                                                .toList()
                                                )
                                                .paymentMethod(pm.getPaymentMethodTypeCode())
                                                .primaryCreditorInstitution(po.getPrimaryCreditorInstitution())
                                                .touchpoint(po.getTouchpoint())
                                                .transferList(
                                                        po.getTransferList()
                                                                .stream()
                                                                .map(
                                                                        t -> new TransferListItemDto()
                                                                                .creditorInstitution(
                                                                                        t.getCreditorInstitution()
                                                                                )
                                                                                .digitalStamp(t.getDigitalStamp())
                                                                                .transferCategory(
                                                                                        t.getTransferCategory()
                                                                                )
                                                                )
                                                                .toList()
                                                ),
                                        po.getIsAllCCP()
                                )

                        ).flatMap(tuple -> afmClient.getFees(tuple.getT1(), maxOccurrences, tuple.getT2()))
                                .map(bo -> {
                                    bo.setBundleOptions(
                                            removeDuplicatePsp(bo.getBundleOptions())
                                    );
                                    return bo;
                                })
                                .map(bo -> bundleOptionToResponse(bo, pm))

                );

    }

    public Mono<CreateSessionResponseDto> createSessionForPaymentMethod(
                                                                        String id,
                                                                        String orderId
    ) {
        return paymentMethodRepository.findById(id)
                .map(PaymentMethodDocument::getPaymentMethodName)
                .map(NpgClient.PaymentMethod::fromServiceName)
                .flatMap(paymentMethod -> {
                    SessionPaymentMethod sessionPaymentMethod = SessionPaymentMethod
                            .fromValue(paymentMethod.serviceName);
                    URI returnUrlBasePath = sessionUrlConfig.basePath();

                    UUID correlationId = UUID.randomUUID();
                    URI resultUrl = returnUrlBasePath.resolve(sessionUrlConfig.outcomeSuffix());
                    URI merchantUrl = returnUrlBasePath;
                    URI cancelUrl = returnUrlBasePath.resolve(sessionUrlConfig.cancelSuffix());
                    String customerId = UUID.randomUUID().toString().replace("-", "").substring(0, 15);
                    URI notificationUrl = UriComponentsBuilder
                            .fromHttpUrl(sessionUrlConfig.notificationUrl())
                            .build(
                                    Map.of(
                                            "orderId",
                                            orderId,
                                            "paymentMethodId",
                                            id
                                    )
                            );

                    return npgClient.buildForm(
                            correlationId, // correlationId
                            merchantUrl, // merchantUrl
                            resultUrl, // resultUrl
                            notificationUrl, // notificationUrl
                            cancelUrl, // cancelUrl
                            orderId, // orderId
                            customerId, // customerId
                            paymentMethod, // paymentMethod
                            npgDefaultApiKey // defaultApiKey
                    ).map(form -> Tuples.of(form, sessionPaymentMethod));
                }).map(data -> {
                    FieldsDto fields = data.getT1();

                    npgSessionsTemplateWrapper
                            .save(
                                    new NpgSessionDocument(
                                            orderId,
                                            fields.getSessionId(),
                                            fields.getSecurityToken(),
                                            null,
                                            null
                                    )
                            );
                    return data;
                }).map(data -> {
                    FieldsDto fields = data.getT1();
                    SessionPaymentMethod paymentMethod = data.getT2();

                    return new CreateSessionResponseDto()
                            .orderId(orderId)
                            .paymentMethodData(
                                    new CardFormFieldsDto()
                                            .paymentMethod(paymentMethod.value)
                                            .form(
                                                    fields.getFields()
                                                            .stream()
                                                            .map(
                                                                    field -> new FieldDto()
                                                                            .id(field.getId())
                                                                            .type(field.getType())
                                                                            .propertyClass(field.getPropertyClass())
                                                                            .src(URI.create(field.getSrc()))
                                                            )
                                                            .collect(Collectors.toList())
                                            )
                            );
                });
    }

    public Mono<SessionPaymentMethodResponseDto> getCardDataInformation(
                                                                        String id,
                                                                        String orderId
    ) {
        log.info(
                "[Payment Method service] Retrieve card data from NPG using paymentMethodId: {} and orderId: {}",
                id,
                orderId
        );
        return paymentMethodRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new PaymentMethodNotFoundException(id)))
                .map(
                        el -> npgSessionsTemplateWrapper.findById(orderId)
                )
                .flatMap(
                        session -> session.map(
                                sx -> {
                                    Mono<SessionPaymentMethodResponseDto> response;
                                    if (sx.cardData() != null) {
                                        log.info("Cache hit for orderId: {}", orderId);
                                        response = Mono.just(
                                                new SessionPaymentMethodResponseDto().bin(sx.cardData().bin())
                                                        .sessionId(sx.sessionId())
                                                        .brand(sx.cardData().circuit())
                                                        .expiringDate(sx.cardData().expiringDate())
                                                        .lastFourDigits(sx.cardData().lastFourDigits())
                                        );
                                    } else {
                                        log.info("Cache miss for orderId: {}", orderId);
                                        response = npgClient.getCardData(
                                                UUID.randomUUID(),
                                                orderId,
                                                npgDefaultApiKey
                                        )
                                                .doOnSuccess(
                                                        el -> npgSessionsTemplateWrapper.save(
                                                                new NpgSessionDocument(
                                                                        sx.orderId(),
                                                                        sx.sessionId(),
                                                                        sx.securityToken(),
                                                                        new CardDataDocument(
                                                                                el.getBin(),
                                                                                el.getLastFourDigits(),
                                                                                el.getExpiringDate(),
                                                                                el.getCircuit()
                                                                        ),
                                                                        null
                                                                )
                                                        )
                                                )
                                                .map(
                                                        el -> new SessionPaymentMethodResponseDto().bin(el.getBin())
                                                                .sessionId(sx.sessionId())
                                                                .brand(el.getCircuit())
                                                                .expiringDate(el.getExpiringDate())
                                                                .lastFourDigits(el.getLastFourDigits())
                                                );
                                    }
                                    return response;
                                }

                        ).orElse(
                                Mono.error(new OrderIdNotFoundException(orderId))
                        )
                );
    }

    public Mono<String> isSessionValid(
                                       String paymentMethodId,
                                       String orderId,
                                       String securityToken
    ) {
        return paymentMethodRepository
                .findById(paymentMethodId)
                .switchIfEmpty(Mono.error(new PaymentMethodNotFoundException(paymentMethodId)))
                .doOnError(e -> log.info("Error while looking for payment method with id {}: ", paymentMethodId, e))
                .map(
                        ignore -> npgSessionsTemplateWrapper.findById(orderId)
                )
                .doOnNext(doc -> log.info("Found session for order id {}: {}", orderId, doc.isPresent()))
                .flatMap(doc -> doc.map(Mono::just).orElse(Mono.error(new OrderIdNotFoundException(orderId))))
                .flatMap(doc -> {
                    String transactionId = doc.transactionId();
                    if (transactionId == null) {
                        return Mono.error(new InvalidSessionException(orderId));
                    } else {
                        return Mono.just(doc);
                    }
                })
                .flatMap(doc -> {
                    if (!doc.securityToken().equals(securityToken)) {
                        log.warn("Invalid security token for requested order id {}", orderId);
                        return Mono.error(new MismatchedSecurityTokenException(orderId, doc.transactionId()));
                    } else {
                        return Mono.just(doc);
                    }
                })
                .mapNotNull(NpgSessionDocument::transactionId)
                .map(TransactionId::new)
                .map(TransactionId::base64);
    }

    public Mono<NpgSessionDocument> updateSession(
                                                  String paymentMethodId,
                                                  String orderId,
                                                  PatchSessionRequestDto updateData
    ) {
        return paymentMethodRepository.findById(paymentMethodId)
                .switchIfEmpty(Mono.error(new PaymentMethodNotFoundException(paymentMethodId)))
                .map(ignore -> npgSessionsTemplateWrapper.findById(orderId))
                .flatMap(document -> document.map(Mono::just).orElse(Mono.empty()))
                .switchIfEmpty(Mono.error(new OrderIdNotFoundException(orderId)))
                .flatMap(document -> {
                    if (document.transactionId() != null) {
                        return Mono.error(
                                new SessionAlreadyAssociatedToTransaction(
                                        orderId,
                                        document.transactionId(),
                                        updateData.getTransactionId()
                                )
                        );
                    } else {
                        return Mono.just(document);
                    }
                })
                .map(d -> {
                    NpgSessionDocument updatedDocument = new NpgSessionDocument(
                            d.orderId(),
                            d.sessionId(),
                            d.securityToken(),
                            d.cardData(),
                            updateData.getTransactionId()
                    );
                    npgSessionsTemplateWrapper.save(updatedDocument);

                    return updatedDocument;
                });
    }

    private List<it.pagopa.generated.ecommerce.gec.v1.dto.TransferDto> removeDuplicatePsp(
                                                                                          List<it.pagopa.generated.ecommerce.gec.v1.dto.TransferDto> transfers
    ) {
        List<String> idPsps = new ArrayList<>();

        return transfers
                .stream()
                .filter(t -> {
                    if (idPsps.contains(t.getIdPsp())) {
                        return false;
                    } else {
                        idPsps.add(t.getIdPsp());
                        return true;
                    }
                })
                .toList();

    }

    private CalculateFeeResponseDto bundleOptionToResponse(
                                                           it.pagopa.generated.ecommerce.gec.v1.dto.BundleOptionDto bundle,
                                                           PaymentMethodDocument paymentMethodDocument
    ) {
        return new CalculateFeeResponseDto()
                .belowThreshold(bundle.getBelowThreshold())
                .paymentMethodName(paymentMethodDocument.getPaymentMethodName())
                .paymentMethodDescription(paymentMethodDocument.getPaymentMethodDescription())
                .paymentMethodStatus(PaymentMethodStatusDto.valueOf(paymentMethodDocument.getPaymentMethodStatus()))
                .bundles(
                        bundle.getBundleOptions() != null ? bundle.getBundleOptions()
                                .stream()
                                .map(
                                        t -> new BundleDto()
                                                .abi(t.getAbi())
                                                .bundleDescription(t.getBundleDescription())
                                                .bundleName(t.getBundleName())
                                                .idBrokerPsp(t.getIdBrokerPsp())
                                                .idBundle(t.getIdBundle())
                                                .idChannel(t.getIdChannel())
                                                .idCiBundle(t.getIdCiBundle())
                                                .idPsp(t.getIdPsp())
                                                .onUs(t.getOnUs())
                                                .paymentMethod(
                                                        // A null value is considered as "any" in the AFM domain
                                                        t.getPaymentMethod() == null
                                                                ? paymentMethodDocument.getPaymentMethodTypeCode()
                                                                : t.getPaymentMethod()
                                                )
                                                .primaryCiIncurredFee(t.getPrimaryCiIncurredFee())
                                                .taxPayerFee(t.getTaxPayerFee())
                                                .touchpoint(t.getTouchpoint())
                                ).toList() : new ArrayList<>()
                );
    }

    private PaymentMethod docToAggregate(PaymentMethodDocument doc) {
        if (doc == null) {
            return null;
        }

        return new PaymentMethod(
                new PaymentMethodID(UUID.fromString(doc.getPaymentMethodID())),
                new PaymentMethodName(doc.getPaymentMethodName()),
                new PaymentMethodDescription(doc.getPaymentMethodDescription()),
                new PaymentMethodStatus(PaymentMethodStatusEnum.valueOf(doc.getPaymentMethodStatus())),
                new PaymentMethodType(doc.getPaymentMethodTypeCode()),
                doc.getPaymentMethodRanges().stream()
                        .map(pair -> new PaymentMethodRange(pair.getFirst(), pair.getSecond()))
                        .toList(),
                new PaymentMethodAsset(doc.getPaymentMethodAsset()),
                NpgClient.PaymentMethod.fromServiceName(doc.getPaymentMethodName())
        );
    }
}
