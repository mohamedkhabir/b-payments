package com.bancoluso.payments.integration;

import com.bancoluso.payments.dto.PaymentRequest;
import com.bancoluso.payments.entity.PaymentStatus;
import com.bancoluso.payments.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Payment API — Integration Tests")
class PaymentControllerIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bancoluso_test")
            .withUsername("test")
            .withPassword("test");
    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PaymentRepository paymentRepository;
    @AfterEach
    void cleanup() {
        paymentRepository.deleteAll();
    }
    @Test
    @DisplayName("POST /v1/payments/events → 202, then GET returns PENDING")
    void ingestAndQuery_happyPath() throws Exception {
        PaymentRequest request = buildRequest("TXN-IT-001", PaymentStatus.PENDING, Instant.parse("2026-04-14T09:00:00Z"));

        mockMvc.perform(post("/v1/payments/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> paymentRepository.findByReferenceId("TXN-IT-001").isPresent());
        mockMvc.perform(get("/v1/payments/TXN-IT-001/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceId").value("TXN-IT-001"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    @DisplayName("Status update: PENDING → PROCESSING with newer timestamp")
    void statusUpdate_newerTimestamp_shouldUpdate() throws Exception {
        String refId = "TXN-IT-002";
        Instant t1 = Instant.parse("2026-04-14T09:00:00Z");
        Instant t2 = Instant.parse("2026-04-14T09:05:00Z");
        mockMvc.perform(post("/v1/payments/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(refId, PaymentStatus.PENDING, t1))))
                .andExpect(status().isAccepted());
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> paymentRepository.findByReferenceId(refId).isPresent());
        mockMvc.perform(post("/v1/payments/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(refId, PaymentStatus.PROCESSING, t2))))
                .andExpect(status().isAccepted());
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> paymentRepository.findByReferenceId(refId)
                        .map(p -> p.getStatus() == PaymentStatus.PROCESSING)
                        .orElse(false));
        mockMvc.perform(get("/v1/payments/" + refId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }
    @Test
    @DisplayName("Stale event must not overwrite a newer status")
    void staleEvent_shouldNotRollbackStatus() throws Exception {
        String refId = "TXN-IT-003";
        Instant t1 = Instant.parse("2026-04-14T09:00:00Z");
        Instant t0 = Instant.parse("2026-04-14T08:50:00Z"); // older
        mockMvc.perform(post("/v1/payments/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(refId, PaymentStatus.PROCESSING, t1))))
                .andExpect(status().isAccepted());
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> paymentRepository.findByReferenceId(refId).isPresent());
        mockMvc.perform(post("/v1/payments/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(refId, PaymentStatus.PENDING, t0))))
                .andExpect(status().isAccepted());
        Thread.sleep(2000);
        mockMvc.perform(get("/v1/payments/" + refId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING")); // must NOT be PENDING
    }
    @Test
    @DisplayName("GET unknown referenceId → 404")
    void getUnknownPayment_shouldReturn404() throws Exception {
        mockMvc.perform(get("/v1/payments/UNKNOWN-999/status"))
                .andExpect(status().isNotFound());
    }
    @Test
    @DisplayName("POST with missing required fields → 400")
    void invalidRequest_shouldReturn400() throws Exception {
        String badRequest = """
                {
                  "referenceId": "TXN-IT-BAD"
                }
                """;

        mockMvc.perform(post("/v1/payments/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /v1/payments returns paginated list")
    void listPayments_shouldReturnPage() throws Exception {
        PaymentRequest req = buildRequest("TXN-IT-LIST-1", PaymentStatus.PENDING,
                Instant.parse("2026-04-14T09:00:00Z"));
        mockMvc.perform(post("/v1/payments/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted());
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> paymentRepository.findByReferenceId("TXN-IT-LIST-1").isPresent());
        mockMvc.perform(get("/v1/payments?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    private PaymentRequest buildRequest(String refId, PaymentStatus status, Instant timestamp) {
        PaymentRequest req = new PaymentRequest();
        req.setReferenceId(refId);
        req.setAmount(new BigDecimal("1500.00"));
        req.setCurrency("EUR");
        req.setDebtorName("Mohamed khabir");
        req.setDebtorIban("PT50000201231234567890154");
        req.setCreditorIban("PT50000201239876543210154");
        req.setValueDate(LocalDate.of(2026, 4, 14));
        req.setStatus(status);
        req.setEventTimestamp(timestamp);
        return req;
    }
}
