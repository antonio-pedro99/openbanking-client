package com.transferwise.openbanking.client.oauth.domain;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.UUID;

public class FapiHeaders extends HttpHeaders {

    private static final String INTERACTION_ID = "x-fapi-interaction-id";
    private static final String FINANCIAL_ID = "x-fapi-financial-id";

    public void setFinancialId(String financialId) {
        set(FINANCIAL_ID, financialId);
    }

    public void setInteractionId(String interactionId) {
        set(INTERACTION_ID, interactionId);
    }

    public String getInteractionId() {
        return getFirst(INTERACTION_ID);
    }

    protected void setBaseValues() {
        setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        setInteractionId(UUID.randomUUID().toString());
    }

    public static FapiHeaders defaultHeaders() {
        FapiHeaders fapiHeaders = new FapiHeaders();
        fapiHeaders.setBaseValues();
        return fapiHeaders;
    }
}
