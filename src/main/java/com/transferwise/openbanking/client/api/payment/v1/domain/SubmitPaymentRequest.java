package com.transferwise.openbanking.client.api.payment.v1.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class SubmitPaymentRequest {

    @JsonProperty("Data")
    private SubmitPaymentRequestData submitPaymentRequestData;

    @JsonProperty("Risk")
    private Risk risk;
}
