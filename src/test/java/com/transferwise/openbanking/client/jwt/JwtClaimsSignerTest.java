package com.transferwise.openbanking.client.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transferwise.openbanking.client.api.payment.common.domain.InstructedAmount;
import com.transferwise.openbanking.client.configuration.AspspDetails;
import com.transferwise.openbanking.client.configuration.TppConfiguration;
import com.transferwise.openbanking.client.test.TestAspspDetails;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwx.HeaderParameterNames;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

class JwtClaimsSignerTest {

    private static final AlgorithmConstraints PS256_ALGORITHM = new AlgorithmConstraints(
        AlgorithmConstraints.ConstraintType.WHITELIST, AlgorithmIdentifiers.RSA_PSS_USING_SHA256);

    private static KeyPair keyPair;
    private static X509Certificate certificate;

    private static ObjectMapper objectMapper;

    private KeySupplier keySupplier;

    private TppConfiguration tppConfiguration;

    private JwtClaimsSigner jwtClaimsSigner;

    @BeforeAll
    static void initAll() throws Exception {
        keyPair = aKeyPair();
        certificate = aCertificate();
        objectMapper = new ObjectMapper();
    }

    @BeforeEach
    void init() {
        keySupplier = Mockito.mock(KeySupplier.class);

        tppConfiguration = aTppConfiguration();

        jwtClaimsSigner = new JwtClaimsSigner(keySupplier, tppConfiguration);
    }

    @Test
    void createSignatureWithJose4JPayloadProducesValidSignature() throws Exception {
        JwtClaims jwtClaims = new JwtClaims();
        jwtClaims.setStringClaim("a-claim", "a-value");
        Map<String, Object> expectedJwtClaims = Map.of("a-claim", "a-value");
        AspspDetails aspspDetails = aAspspDetails();

        Mockito.when(keySupplier.getSigningKey(aspspDetails)).thenReturn(keyPair.getPrivate());

        String serialisedSignature = jwtClaimsSigner.createSignature(jwtClaims, aspspDetails);
        JsonWebSignature jsonWebSignature = parseSignature(serialisedSignature);

        Assertions.assertEquals(expectedJwtClaims,
            objectMapper.readValue(jsonWebSignature.getPayload(), Map.class));
        Assertions.assertEquals(aspspDetails.getSigningKeyId(), jsonWebSignature.getKeyIdHeaderValue());
    }

    @Test
    void createSignatureWithCustomPayloadProducesValidSignature() throws Exception {
        InstructedAmount jwtClaims = anInstructedAmount();
        Map<String, Object> expectedJwtClaims = Map.of("Amount", "10.50", "Currency", "GBP");
        AspspDetails aspspDetails = aAspspDetails();

        Mockito.when(keySupplier.getSigningKey(aspspDetails)).thenReturn(keyPair.getPrivate());

        String serialisedSignature = jwtClaimsSigner.createSignature(jwtClaims, aspspDetails);
        JsonWebSignature jsonWebSignature = parseSignature(serialisedSignature);

        Assertions.assertEquals(expectedJwtClaims,
            objectMapper.readValue(jsonWebSignature.getPayload(), Map.class));
        Assertions.assertEquals(aspspDetails.getSigningKeyId(), jsonWebSignature.getKeyIdHeaderValue());
    }

    @Test
    void createDetachedSignatureProducesValidSignature() throws Exception {
        InstructedAmount jwtClaims = anInstructedAmount();
        AspspDetails aspspDetails = aAspspDetails();

        Mockito.when(keySupplier.getSigningKey(aspspDetails)).thenReturn(keyPair.getPrivate());

        String serialisedSignature = jwtClaimsSigner.createDetachedSignature(jwtClaims, aspspDetails);
        JsonWebSignature jsonWebSignature = parseDetachedSignature(serialisedSignature, jwtClaims);

        Assertions.assertTrue(jsonWebSignature.verifySignature());
        Assertions.assertEquals(aspspDetails.getSigningKeyId(), jsonWebSignature.getKeyIdHeaderValue());
        Assertions.assertEquals(Boolean.FALSE,
            jsonWebSignature.getObjectHeader(HeaderParameterNames.BASE64URL_ENCODE_PAYLOAD));
        Assertions.assertEquals(tppConfiguration.getOrganisationId() + "/" + tppConfiguration.getSoftwareStatementId(),
            jsonWebSignature.getHeader(OpenBankingJwsHeaders.OPEN_BANKING_ISS));
        Assertions.assertEquals("openbanking.org.uk",
            jsonWebSignature.getHeader(OpenBankingJwsHeaders.OPEN_BANKING_TAN));

        long generatedAtTimeDifference = NumericDate.now().getValue() -
            ((long) jsonWebSignature.getObjectHeader(OpenBankingJwsHeaders.OPEN_BANKING_IAT));
        Assertions.assertTrue(generatedAtTimeDifference <= 5);

        Assertions.assertEquals(
            Arrays.asList(HeaderParameterNames.BASE64URL_ENCODE_PAYLOAD,
                OpenBankingJwsHeaders.OPEN_BANKING_IAT,
                OpenBankingJwsHeaders.OPEN_BANKING_ISS,
                OpenBankingJwsHeaders.OPEN_BANKING_TAN),
            jsonWebSignature.getObjectHeader(HeaderParameterNames.CRITICAL));
    }

    @Test
    void createDetachedSignatureProducesValidSignatureWithNonDirectoryIssFormat() throws Exception {
        InstructedAmount jwtClaims = anInstructedAmount();
        AspspDetails aspspDetails = TestAspspDetails.builder()
            .signingAlgorithm(AlgorithmIdentifiers.RSA_PSS_USING_SHA256)
            .signingKeyId("signing-key-id")
            .detachedSignatureUsesDirectoryIssFormat(false)
            .build();

        Mockito.when(keySupplier.getSigningKey(aspspDetails)).thenReturn(keyPair.getPrivate());
        Mockito.when(keySupplier.getSigningCertificate(aspspDetails)).thenReturn(certificate);

        String serialisedSignature = jwtClaimsSigner.createDetachedSignature(jwtClaims, aspspDetails);
        JsonWebSignature jsonWebSignature = parseDetachedSignature(serialisedSignature, jwtClaims);

        Assertions.assertTrue(jsonWebSignature.verifySignature());
        Assertions.assertEquals(aspspDetails.getSigningKeyId(), jsonWebSignature.getKeyIdHeaderValue());
        Assertions.assertEquals(Boolean.FALSE,
            jsonWebSignature.getObjectHeader(HeaderParameterNames.BASE64URL_ENCODE_PAYLOAD));
        Assertions.assertEquals(certificate.getSubjectX500Principal().getName(),
            jsonWebSignature.getHeader(OpenBankingJwsHeaders.OPEN_BANKING_ISS));
        Assertions.assertEquals("openbanking.org.uk",
            jsonWebSignature.getHeader(OpenBankingJwsHeaders.OPEN_BANKING_TAN));

        long generatedAtTimeDifference = NumericDate.now().getValue() -
            ((long) jsonWebSignature.getObjectHeader(OpenBankingJwsHeaders.OPEN_BANKING_IAT));
        Assertions.assertTrue(generatedAtTimeDifference <= 5);

        Assertions.assertEquals(
            Arrays.asList(HeaderParameterNames.BASE64URL_ENCODE_PAYLOAD,
                OpenBankingJwsHeaders.OPEN_BANKING_IAT,
                OpenBankingJwsHeaders.OPEN_BANKING_ISS,
                OpenBankingJwsHeaders.OPEN_BANKING_TAN),
            jsonWebSignature.getObjectHeader(HeaderParameterNames.CRITICAL));
    }

    private static KeyPair aKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    private static X509Certificate aCertificate() throws Exception {
        X509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
            new X500Name("CN=OpenBanking Pre-Production Issuing CA,O=OpenBanking,C=GB"),
            BigInteger.ONE,
            Date.from(LocalDateTime.now().minusWeeks(1).toInstant(ZoneOffset.UTC)),
            Date.from(LocalDateTime.now().plusWeeks(1).toInstant(ZoneOffset.UTC)),
            new X500Name("CN=ORGANISATION_ID,O=TransferWise LTD,C=GB"),
            keyPair.getPublic());
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA")
            .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
            .setProvider(new BouncyCastleProvider())
            .getCertificate(certificateBuilder.build(contentSigner));
    }

    private TppConfiguration aTppConfiguration() {
        TppConfiguration tppConfiguration = new TppConfiguration();
        tppConfiguration.setOrganisationId("organisation-id");
        tppConfiguration.setSoftwareStatementId("software-statement-id");
        return tppConfiguration;
    }

    private AspspDetails aAspspDetails() {
        return TestAspspDetails.builder()
            .signingAlgorithm(AlgorithmIdentifiers.RSA_PSS_USING_SHA256)
            .signingKeyId("signing-key-id")
            .detachedSignatureUsesDirectoryIssFormat(true)
            .build();
    }

    private InstructedAmount anInstructedAmount() {
        return new InstructedAmount("10.50", "GBP");
    }

    private JsonWebSignature parseSignature(String serialisedSignature) throws Exception {
        JsonWebSignature jsonWebSignature = new JsonWebSignature();
        jsonWebSignature.setCompactSerialization(serialisedSignature);
        jsonWebSignature.setKey(keyPair.getPublic());
        jsonWebSignature.setAlgorithmConstraints(PS256_ALGORITHM);
        return jsonWebSignature;
    }

    private JsonWebSignature parseDetachedSignature(String serialisedSignature, Object detachedPayload)
        throws Exception {
        JsonWebSignature jsonWebSignature = new JsonWebSignature();
        jsonWebSignature.setCompactSerialization(serialisedSignature);
        jsonWebSignature.setPayload(objectMapper.writeValueAsString(detachedPayload));
        jsonWebSignature.setKey(keyPair.getPublic());
        jsonWebSignature.setAlgorithmConstraints(PS256_ALGORITHM);
        jsonWebSignature.setKnownCriticalHeaders(HeaderParameterNames.BASE64URL_ENCODE_PAYLOAD,
            OpenBankingJwsHeaders.OPEN_BANKING_IAT,
            OpenBankingJwsHeaders.OPEN_BANKING_ISS,
            OpenBankingJwsHeaders.OPEN_BANKING_TAN);
        return jsonWebSignature;
    }
}
