package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.input.ClientExternalRequestDTO;
import com.softmax.shardaos.dto.input.ConsultantExternalRequestDTO;
import com.softmax.shardaos.dto.input.ProjectInquiryExternalDTO;
import com.softmax.shardaos.dto.output.ExternalApiResponseDTO;
import com.softmax.shardaos.security.ExternalApiKeyValidator;
import com.softmax.shardaos.service.ExternalApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/external")
public class ExternalApiController {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiController.class);

    @Autowired private ExternalApiService externalApiService;
    @Autowired private ExternalApiKeyValidator apiKeyValidator;

    // =========================================================
    // API 1: CONSULTANT APPLICATION (DB insert)
    // =========================================================
    @PostMapping("/consultant")
    public ResponseEntity<ExternalApiResponseDTO> createConsultant(
            @Valid @RequestBody ConsultantExternalRequestDTO request,
            HttpServletRequest httpRequest) {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("[CONTROLLER] ▶ POST /api/v1/external/consultant");
        log.info("[CONTROLLER]   clientIp : {}", httpRequest.getRemoteAddr());
        log.info("[CONTROLLER]   payload  : {}", request);
        log.info("───────────────────────────────────────────────────────────");

        String apiKey = apiKeyValidator.extractApiKey(httpRequest);
        log.debug("[CONTROLLER] Extracted API key present? {}", apiKey != null && !apiKey.isBlank());

        if (!apiKeyValidator.isValidConsultantKey(apiKey)) {
            log.warn("[CONTROLLER] ⚠ UNAUTHORIZED — invalid/missing consultant API key. ip={}",
                    httpRequest.getRemoteAddr());

            ExternalApiResponseDTO error = ExternalApiResponseDTO.builder()
                    .success(false)
                    .message("Invalid or missing API key")
                    .build();

            log.info("[CONTROLLER] ◀ POST /api/v1/external/consultant → 401 UNAUTHORIZED, body={}",
                    error.getMessage());
            log.info("═══════════════════════════════════════════════════════════");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        log.info("[CONTROLLER] API key VALID → delegating to ExternalApiService.createConsultantFromExternal()");

        ExternalApiResponseDTO response = externalApiService.createConsultantFromExternal(request);

        log.info("[CONTROLLER] ◀ POST /api/v1/external/consultant → 201 CREATED, response={}",
                response);
        log.info("═══════════════════════════════════════════════════════════");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================
    // API 2: CLIENT REGISTRATION (DB insert)
    // =========================================================
    @PostMapping("/client")
    public ResponseEntity<ExternalApiResponseDTO> createClient(
            @Valid @RequestBody ClientExternalRequestDTO request,
            HttpServletRequest httpRequest) {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("[CONTROLLER] ▶ POST /api/v1/external/client");
        log.info("[CONTROLLER]   clientIp : {}", httpRequest.getRemoteAddr());
        log.info("[CONTROLLER]   payload  : {}", request);
        log.info("───────────────────────────────────────────────────────────");

        String apiKey = apiKeyValidator.extractApiKey(httpRequest);
        log.debug("[CONTROLLER] Extracted API key present? {}", apiKey != null && !apiKey.isBlank());

        if (!apiKeyValidator.isValidClientKey(apiKey)) {
            log.warn("[CONTROLLER] ⚠ UNAUTHORIZED — invalid/missing client API key. ip={}",
                    httpRequest.getRemoteAddr());

            ExternalApiResponseDTO error = ExternalApiResponseDTO.builder()
                    .success(false)
                    .message("Invalid or missing API key")
                    .build();

            log.info("[CONTROLLER] ◀ POST /api/v1/external/client → 401 UNAUTHORIZED, body={}",
                    error.getMessage());
            log.info("═══════════════════════════════════════════════════════════");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        log.info("[CONTROLLER] API key VALID → delegating to ExternalApiService.createClientFromExternal()");

        ExternalApiResponseDTO response = externalApiService.createClientFromExternal(request);

        log.info("[CONTROLLER] ◀ POST /api/v1/external/client → 201 CREATED, response={}",
                response);
        log.info("═══════════════════════════════════════════════════════════");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================
    // API 3: PROJECT INQUIRY (NO DB insert, mail ONLY to Head Director)
    // =========================================================
    @PostMapping("/project-inquiry")
    public ResponseEntity<ExternalApiResponseDTO> submitProjectInquiry(
            @Valid @RequestBody ProjectInquiryExternalDTO request,
            HttpServletRequest httpRequest) {

        log.info("═══════════════════════════════════════════════════════════");
        log.info("[CONTROLLER] ▶ POST /api/v1/external/project-inquiry");
        log.info("[CONTROLLER]   clientIp : {}", httpRequest.getRemoteAddr());
        log.info("[CONTROLLER]   payload  : {}", request);
        log.info("───────────────────────────────────────────────────────────");

        String apiKey = apiKeyValidator.extractApiKey(httpRequest);
        log.debug("[CONTROLLER] Extracted API key present? {}", apiKey != null && !apiKey.isBlank());

        if (!apiKeyValidator.isValidProjectKey(apiKey)) {
            log.warn("[CONTROLLER] ⚠ UNAUTHORIZED — invalid/missing project inquiry API key. ip={}",
                    httpRequest.getRemoteAddr());

            ExternalApiResponseDTO error = ExternalApiResponseDTO.builder()
                    .success(false)
                    .message("Invalid or missing API key")
                    .build();

            log.info("[CONTROLLER] ◀ POST /api/v1/external/project-inquiry → 401 UNAUTHORIZED, body={}",
                    error.getMessage());
            log.info("═══════════════════════════════════════════════════════════");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        log.info("[CONTROLLER] API key VALID → delegating to ExternalApiService.submitProjectInquiry()");

        ExternalApiResponseDTO response = externalApiService.submitProjectInquiry(request);

        log.info("[CONTROLLER] ◀ POST /api/v1/external/project-inquiry → 201 CREATED, response={}",
                response);
        log.info("═══════════════════════════════════════════════════════════");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}










//package com.softmax.shardaos.controller;
//
//import com.softmax.shardaos.dto.input.ClientExternalRequestDTO;
//import com.softmax.shardaos.dto.input.ConsultantExternalRequestDTO;
//import com.softmax.shardaos.dto.input.ProjectInquiryExternalDTO;
//import com.softmax.shardaos.dto.output.ExternalApiResponseDTO;
//import com.softmax.shardaos.security.ExternalApiKeyValidator;
//import com.softmax.shardaos.service.ExternalApiService;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.validation.Valid;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/v1/external")
//public class ExternalApiController {
//
//    @Autowired private ExternalApiService externalApiService;
//    @Autowired private ExternalApiKeyValidator apiKeyValidator;
//
//    // =========================================================
//    // API 1: CONSULTANT APPLICATION (DB insert)
//    // =========================================================
//    @PostMapping("/consultant")
//    public ResponseEntity<ExternalApiResponseDTO> createConsultant(
//            @Valid @RequestBody ConsultantExternalRequestDTO request,
//            HttpServletRequest httpRequest) {
//
//        if (!apiKeyValidator.isValidConsultantKey(apiKeyValidator.extractApiKey(httpRequest))) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
//                    ExternalApiResponseDTO.builder()
//                            .success(false)
//                            .message("Invalid or missing API key")
//                            .build());
//        }
//
//        return ResponseEntity.status(HttpStatus.CREATED)
//                .body(externalApiService.createConsultantFromExternal(request));
//    }
//
//    // =========================================================
//    // API 2: CLIENT REGISTRATION (DB insert)
//    // =========================================================
//    @PostMapping("/client")
//    public ResponseEntity<ExternalApiResponseDTO> createClient(
//            @Valid @RequestBody ClientExternalRequestDTO request,
//            HttpServletRequest httpRequest) {
//
//        if (!apiKeyValidator.isValidClientKey(apiKeyValidator.extractApiKey(httpRequest))) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
//                    ExternalApiResponseDTO.builder()
//                            .success(false)
//                            .message("Invalid or missing API key")
//                            .build());
//        }
//
//        return ResponseEntity.status(HttpStatus.CREATED)
//                .body(externalApiService.createClientFromExternal(request));
//    }
//
//    // =========================================================
//    // API 3: PROJECT INQUIRY (NO DB insert, mail ONLY to Head Director)
//    // =========================================================
//    @PostMapping("/project-inquiry")
//    public ResponseEntity<ExternalApiResponseDTO> submitProjectInquiry(
//            @Valid @RequestBody ProjectInquiryExternalDTO request,
//            HttpServletRequest httpRequest) {
//
//        if (!apiKeyValidator.isValidProjectKey(apiKeyValidator.extractApiKey(httpRequest))) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
//                    ExternalApiResponseDTO.builder()
//                            .success(false)
//                            .message("Invalid or missing API key")
//                            .build());
//        }
//
//        return ResponseEntity.status(HttpStatus.CREATED)
//                .body(externalApiService.submitProjectInquiry(request));
//    }
//}