package it.pagopa.ecommerce.payment.instruments.infrastructure;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import it.pagopa.ecommerce.payment.instruments.domain.valueobjects.Psp;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Document(collection = "payment-instruments")
public class PaymentInstrumentDocument {

    @Id
    private String paymentInstrumentID;
    private String paymentInstrumentName;
    private String paymentInstrumentDescription;
    private List<Psp> psp;
    private String paymentInstrumentStatus;
}
