package it.pagopa.ecommerce.payment.methods.utils;

import it.pagopa.ecommerce.payment.methods.domain.aggregates.PaymentMethod;
import it.pagopa.ecommerce.payment.methods.domain.aggregates.Psp;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PaymentMethodAsset;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PaymentMethodDescription;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PaymentMethodID;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PaymentMethodName;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PaymentMethodRange;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PaymentMethodStatus;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PaymentMethodType;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspAmount;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspBrokerName;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspBusinessName;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspChannelCode;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspCode;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspDescription;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspFee;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspLanguage;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspPaymentMethodType;
import it.pagopa.ecommerce.payment.methods.domain.valueobjects.PspStatus;
import it.pagopa.ecommerce.payment.methods.infrastructure.PaymentMethodDocument;
import it.pagopa.ecommerce.payment.methods.infrastructure.PspDocument;
import it.pagopa.ecommerce.payment.methods.infrastructure.PspDocumentKey;
import it.pagopa.ecommerce.payment.methods.server.model.*;
import org.springframework.data.util.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TestUtil {

    static final String TEST_NAME = "Test";
    static final String TEST_DESC = "test";
    static final PaymentMethodRequestDto.StatusEnum TEST_STATUS = PaymentMethodRequestDto.StatusEnum.ENABLED;
    static final String TEST_TYPE_CODE = "test";

    static final String TEST_LANG = "IT";
    static final Long TEST_AMOUNT = 1L;

    static final String TEST_ASSET = "test";

    static final UUID TEST_ID = UUID.randomUUID();
    static final String PSP_TEST_CODE = "001";
    static final String PSP_TEST_NAME = "test";
    static final String PSP_TEST_DESC = "test";
    static final String PSP_TEST_CHANNEL = "channel0";

    public static PaymentMethod getPaymentMethod() {
        return new PaymentMethod(
                new PaymentMethodID(TEST_ID),
                new PaymentMethodName("Test"),
                new PaymentMethodDescription("Test"),
                new PaymentMethodStatus(PaymentMethodStatusEnum.ENABLED),
                new PaymentMethodType(TEST_TYPE_CODE),
                List.of(new PaymentMethodRange(0L, 100L)),
                new PaymentMethodAsset(TEST_ASSET)
        );
    }

    public static PaymentMethodRequestDto getPaymentMethodRequest() {
        return new PaymentMethodRequestDto()
                .name(TEST_NAME)
                .description(TEST_DESC)
                .status(TEST_STATUS)
                .paymentTypeCode(TEST_TYPE_CODE)
                .ranges(List.of(new RangeDto().max(100L).min(0L)))
                .asset(TEST_ASSET);
    }

    public static PaymentMethodResponseDto getPaymentMethodResponse(PaymentMethod paymentMethod) {
        return new PaymentMethodResponseDto()
                .id(paymentMethod.getPaymentMethodID().value().toString())
                .name(paymentMethod.getPaymentMethodName().value())
                .asset(paymentMethod.getPaymentMethodAsset().value())
                .description(paymentMethod.getPaymentMethodDescription().value())
                .status(
                        PaymentMethodResponseDto.StatusEnum
                                .fromValue(paymentMethod.getPaymentMethodStatus().value().getCode())
                )
                .paymentTypeCode(paymentMethod.getPaymentMethodTypeCode().value())
                .ranges(List.of(new RangeDto().min(0L).max(100L)));
    }

    public static long getTestAmount() {
        return TEST_AMOUNT;
    }

    /*
     * public static PspDto getTestPspDto() { return new PspDto()
     * .code(PSP_TEST_CODE) .brokerName(PSP_TEST_NAME) .description(PSP_TEST_DESC)
     * .businessName(PSP_TEST_NAME) .status(PspDto.StatusEnum.ENABLED)
     * .channelCode(PSP_TEST_CHANNEL); }
     */

    public static Psp getTestPsp() {
        return new Psp(
                new PspCode(PSP_TEST_CODE),
                new PspPaymentMethodType("PO"),
                new PspStatus(PaymentMethodStatusEnum.ENABLED),
                new PspBusinessName(""),
                new PspBrokerName(""),
                new PspDescription(""),
                new PspLanguage(LanguageEnum.IT),
                new PspAmount(BigInteger.valueOf(0)),
                new PspAmount(BigInteger.valueOf(100)),
                new PspChannelCode("AB0"),
                new PspFee(BigInteger.valueOf(100))
        );
    }

    public static String getTestLang() {
        return TEST_LANG;
    }

    public static String getTestPaymentType() {
        return TEST_TYPE_CODE;
    }

    /*
     * public static ServicesDto getTestServices() { return new ServicesDto()
     * .services( List.of( new ServiceDto() .abiCode("TEST")
     * .channelCode("CHANNEL=") .languageCode(ServiceDto.LanguageCodeEnum.IT)
     * .conventionCode("TEST") .pspCode("TEST") .paymentTypeCode("PO")
     * .pspBusinessName("TEST") .brokerPspCode("TEST") .serviceName("TEST")
     * .serviceDescription("TEST") .minimumAmount(0.0) .maximumAmount(100.0)
     * .fixedCost(100.0) ) ) .pageInfo( new PageInfoDto() .totalPages(1) .limit(50)
     * .page(0) .itemsFound(1) ); }
     *
     */
    public static PaymentMethodDocument getTestPaymentDoc(PaymentMethod paymentMethod) {
        return new PaymentMethodDocument(
                paymentMethod.getPaymentMethodID().value().toString(),
                paymentMethod.getPaymentMethodName().value(),
                paymentMethod.getPaymentMethodDescription().value(),
                paymentMethod.getPaymentMethodStatus().value().toString(),
                paymentMethod.getPaymentMethodAsset().value(),
                paymentMethod.getPaymentMethodRanges().stream().map(r -> Pair.of(r.min(), r.max()))
                        .collect(Collectors.toList()),
                paymentMethod.getPaymentMethodTypeCode().value()
        );
    }

    public static PspDocument getTestPspDoc(Psp psp) {
        return new PspDocument(
                new PspDocumentKey(
                        psp.getPspCode().value(),
                        psp.getPspPaymentMethodType().value(),
                        psp.getPspChannelCode().value(),
                        psp.getPspLanguage().value().getLanguage()
                ),
                psp.getPspStatus().value().getCode(),
                psp.getPspBusinessName().value(),
                psp.getPspBrokerName().value(),
                psp.getPspDescription().value(),
                psp.getPspMinAmount().value().longValue(),
                psp.getPspMaxAmount().value().longValue(),
                psp.getPspFixedCost().value().longValue()
        );
    }

    public static it.pagopa.generated.ecommerce.gec.v1.dto.BundleOptionDto getBundleOptionDto() {
        List<it.pagopa.generated.ecommerce.gec.v1.dto.TransferDto> transferList = new ArrayList<>();
        transferList.add(
                new it.pagopa.generated.ecommerce.gec.v1.dto.TransferDto().abi("abiTest")
                        .bundleDescription("descriptionTest")
                        .bundleName("bundleNameTest")
                        .idBrokerPsp("idBrokerPspTest")
                        .idBundle("idBundleTest")
                        .idChannel("idChannelTest")
                        .idCiBundle("idCiBundleTest")
                        .idPsp("idPspTest")
                        .onUs(true)
                        .paymentMethod("idPaymentMethodTest")
                        .primaryCiIncurredFee(BigInteger.ZERO.longValue())
                        .taxPayerFee(BigInteger.ZERO.longValue())
                        .touchpoint("CHECKOUT")
        );
        return new it.pagopa.generated.ecommerce.gec.v1.dto.BundleOptionDto()
                .belowThreshold(true)
                .bundleOptions(
                        transferList
                );
    }

    public static BundleOptionDto getBundleOptionDtoResponse(
                                                             it.pagopa.generated.ecommerce.gec.v1.dto.BundleOptionDto gecResponse
    ) {
        return new BundleOptionDto()
                .belowThreshold(gecResponse.getBelowThreshold())
                .bundleOptions(
                        gecResponse.getBundleOptions() != null ? gecResponse.getBundleOptions()
                                .stream()
                                .map(
                                        t -> new TransferDto()
                                                .abi(t.getAbi())
                                                .bundleDescription(t.getBundleDescription())
                                                .bundleName(t.getBundleName())
                                                .idBrokerPsp(t.getIdBrokerPsp())
                                                .idBundle(t.getIdBundle())
                                                .idChannel(t.getIdChannel())
                                                .idCiBundle(t.getIdCiBundle())
                                                .idPsp(t.getIdPsp())
                                                .onUs(t.getOnUs())
                                                .paymentMethod(t.getPaymentMethod())
                                                .primaryCiIncurredFee(t.getPrimaryCiIncurredFee())
                                                .taxPayerFee(t.getTaxPayerFee())
                                                .touchpoint(t.getTouchpoint())
                                ).collect(Collectors.toList()) : new ArrayList<>()
                );
    }

    public static PaymentOptionDto getPaymentOptionRequest() {
        return new PaymentOptionDto()
                .paymentAmount(BigInteger.TEN.longValue())
                .paymentMethodId("paymentMethodID")
                .primaryCreditorInstitution("CF")
                .bin("BIN_TEST")
                .touchpoint("CHECKOUT")
                .addIdPspListItem("string")
                .idPspList(new ArrayList<>(List.of("first", "second")))
                .transferList(
                        new ArrayList<>(
                                List.of(
                                        new TransferListItemDto()
                                                .transferCategory("category")
                                                .creditorInstitution("creditorInstitution")
                                                .digitalStamp(true)
                                )
                        )
                );
    }
}
