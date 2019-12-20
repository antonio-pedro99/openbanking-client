package com.transferwise.openbanking.client.api.payment.v1.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.transferwise.openbanking.client.api.payment.common.domain.Initiation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class SetupPaymentRequestData {

    @JsonProperty("Initiation")
    private Initiation initiation;
}
