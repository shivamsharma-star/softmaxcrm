//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.PartnerCreateClientRequest;
//import com.softmax.shardaos.dto.input.PartnerCreateProjectRequest;
//import com.softmax.shardaos.dto.input.PartnerSelfRegisterRequest;
//import com.softmax.shardaos.dto.output.PartnerClientResponseDTO;
//import com.softmax.shardaos.dto.output.PartnerDashboardDTO;
//import com.softmax.shardaos.dto.output.PartnerProjectSummaryDTO;
//import com.softmax.shardaos.dto.output.PartnerResponseDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.stream.Collectors;
//
//@Service
//public class PartnerManagementService {
//
//    private static final Logger log = LoggerFactory.getLogger(PartnerManagementService.class);
//
//    private static final Set<String> VALID_AGREEMENT_TYPES = Set.of("PERCENTAGE", "MONTHLY", "FIXED");
//    private static final Set<String> VALID_SERVICES = Set.of(
//            "CLIENT_RELATIONSHIP", "SELLING", "SECURITY",
//            "MARKETING", "SUPPORT", "DEVELOPMENT", "OTHER"
//    );
//    private static final Set<String> VALID_PARTNER_STATUSES =
//            Set.of("PENDING", "APPROVED", "BLOCKED", "DEACTIVATED");
//
//    private static final Long HEAD_DEPARTMENT_ID = 1L;
//
//    private final DateTimeFormatter emailDateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
//
//    @Autowired private PartnerDetailsRepository partnerRepository;
//    @Autowired private PartnerClientMappingRepository mappingRepository;
//    @Autowired private UserRepository userRepository;
//    @Autowired private UserTypeRepository userTypeRepository;
//    @Autowired private ClientDetailsRepository clientDetailsRepository;
//    @Autowired private ProjectDetailsRepository projectRepository;
//    @Autowired private PaymentDetailsRepository paymentDetailsRepository;
//    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
//    @Autowired private DepartmentRepository departmentRepository;
//    @Autowired private EmailService emailService;
//
//    // =====================================================================
//    // 1. SELF REGISTRATION
//    // =====================================================================
//
////    @Transactional
////    public PartnerResponseDTO selfRegister(PartnerSelfRegisterRequest req) {
////
////        log.info("[PARTNER-SELF-REGISTER] email={}, company={}", req.getEmail(), req.getCompanyName());
////
////        // ---- Validation ----
////        if (isBlank(req.getEmail()))         throw new RuntimeException("Email is required");
////        if (isBlank(req.getCompanyName()))   throw new RuntimeException("Company name is required");
////        if (isBlank(req.getAgreementPdfDriveLink()))
////            throw new RuntimeException("Agreement PDF drive link is required");
////        if (isBlank(req.getAgreementType()))
////            throw new RuntimeException("Agreement type is required");
////
////        final String agreementType = req.getAgreementType().toUpperCase();
////        if (!VALID_AGREEMENT_TYPES.contains(agreementType))
////            throw new RuntimeException("Invalid agreementType. Allowed: " + VALID_AGREEMENT_TYPES);
////
////        if (req.getServicesOffered() != null) {
////            for (String s : req.getServicesOffered()) {
////                if (s == null) continue;
////                if (!VALID_SERVICES.contains(s.toUpperCase()))
////                    throw new RuntimeException("Invalid service: " + s + ". Allowed: " + VALID_SERVICES);
////            }
////        }
////
////        if (!isBlank(req.getCompanyEmail())
////                && partnerRepository.existsByCompanyEmail(req.getCompanyEmail())
////                && !req.getCompanyEmail().equalsIgnoreCase(req.getEmail())) {
////            throw new RuntimeException("A partner with this company email already exists");
////        }
////
////        // ---- 1) Upsert User ----
////        User user = userRepository.findByEmail(req.getEmail()).orElseGet(() -> {
////            User u = new User();
////            u.setUid(isBlank(req.getUid()) ? "PARTNER_" + UUID.randomUUID() : req.getUid());
////            u.setEmail(req.getEmail());
////            u.setEmailVerified(false);
////            u.setIsProfileCompleted(false);
////            return u;
////        });
////
////        if (!isBlank(req.getName()))        user.setName(req.getName());
////        if (!isBlank(req.getPhoneNumber())) user.setPhoneNumber(req.getPhoneNumber());
////        if (!isBlank(req.getGender()))      user.setGender(req.getGender());
////        if (!isBlank(req.getPhotoUrl()))    user.setPhotoUrl(req.getPhotoUrl());
////        if (!isBlank(req.getDob())) {
////            try { user.setDob(LocalDate.parse(req.getDob())); } catch (Exception ignored) {}
////        }
////
////        // ✅ FIX: final variable for lambda safety
////        final User savedUser = userRepository.save(user);
////
////        // ---- 2) UserType PARTNER (PENDING) ----
////        Optional<UserType> existingPartnerType =
////                userTypeRepository.findByUserUserIdAndPtype(savedUser.getUserId(), "PARTNER");
////
////        if (existingPartnerType.isEmpty()) {
////            UserType ut = new UserType();
////            ut.setUser(savedUser);
////            ut.setPtype("PARTNER");
////            ut.setStype(null);
////            ut.setIsapproved("PENDING");
////            ut.setCreatedAt(LocalDateTime.now());
////            userTypeRepository.save(ut);
////            log.info("[PARTNER-SELF-REGISTER] UserType PARTNER created (PENDING) for userId={}",
////                    savedUser.getUserId());
////        } else {
////            if ("APPROVED".equalsIgnoreCase(existingPartnerType.get().getIsapproved())) {
////                throw new RuntimeException("This user is already an approved partner");
////            }
////        }
////
////        // ---- 3) PartnerDetails ----
////        PartnerDetails partner = partnerRepository.findByUserUserId(savedUser.getUserId())
////                .orElseGet(() -> {
////                    PartnerDetails p = new PartnerDetails();
////                    p.setUser(savedUser);           // ✅ effectively final
////                    p.setPartnerCode(generatePartnerCode());
////                    return p;
////                });
////
////        if ("APPROVED".equalsIgnoreCase(partner.getPartnerStatus())) {
////            throw new RuntimeException("This partner is already approved");
////        }
////
////        partner.setCompanyName(req.getCompanyName());
////        partner.setCompanyEmail(req.getCompanyEmail());
////        partner.setCompanyMobileNumber(req.getCompanyMobileNumber());
////        partner.setCompanyWebsite(req.getCompanyWebsite());
////        partner.setCompanyAddress(req.getCompanyAddress());
////        partner.setContactPersonName(req.getContactPersonName());
////        partner.setContactPersonDesignation(req.getContactPersonDesignation());
////
////        if (req.getServicesOffered() != null) {
////            partner.setServicesOffered(
////                    req.getServicesOffered().stream()
////                            .filter(Objects::nonNull)
////                            .map(String::toUpperCase)
////                            .collect(Collectors.toSet())
////            );
////        }
////
////        partner.setAgreementType(agreementType);
////        partner.setAgreementValue(req.getAgreementValue());
////        partner.setAgreementPdfDriveLink(req.getAgreementPdfDriveLink());
////        partner.setAgreementStartDate(req.getAgreementStartDate());
////        partner.setAgreementEndDate(req.getAgreementEndDate());
////        partner.setInternalNotes(req.getInternalNotes());
////        partner.setPartnerStatus("PENDING");
////        partner.setIsApproved(false);
////
////        partner = partnerRepository.save(partner);
////        log.info("[PARTNER-SELF-REGISTER] PartnerDetails saved → partnerId={}, code={}",
////                partner.getPartnerId(), partner.getPartnerCode());
////
////        // ---- 4) Welcome email ----
////        sendPartnerWelcomeEmail(partner);
////
////        return toPartnerDTO(partner);
////    }
//@Transactional
//public PartnerResponseDTO selfRegister(Long userId, PartnerSelfRegisterRequest req) {
//
//    log.info("[PARTNER-SELF-REGISTER] userId={}, company={}", userId, req.getCompanyName());
//
//    // ---- 1) User must already exist (JWT se aaya) ----
//    User user = userRepository.findById(userId)
//            .orElseThrow(() -> new RuntimeException("User not found. Please sign in first."));
//
//    // ---- 2) Validation ----
//    if (isBlank(req.getCompanyName()))
//        throw new RuntimeException("Company name is required");
//    if (isBlank(req.getAgreementPdfDriveLink()))
//        throw new RuntimeException("Agreement PDF drive link is required");
//    if (isBlank(req.getAgreementType()))
//        throw new RuntimeException("Agreement type is required");
//
//    final String agreementType = req.getAgreementType().toUpperCase();
//    if (!VALID_AGREEMENT_TYPES.contains(agreementType))
//        throw new RuntimeException("Invalid agreementType. Allowed: " + VALID_AGREEMENT_TYPES);
//
//    if (req.getServicesOffered() != null) {
//        for (String s : req.getServicesOffered()) {
//            if (s == null) continue;
//            if (!VALID_SERVICES.contains(s.toUpperCase()))
//                throw new RuntimeException("Invalid service: " + s + ". Allowed: " + VALID_SERVICES);
//        }
//    }
//
//    if (!isBlank(req.getCompanyEmail())
//            && partnerRepository.existsByCompanyEmail(req.getCompanyEmail())
//            && !req.getCompanyEmail().equalsIgnoreCase(user.getEmail())) {
//        throw new RuntimeException("A partner with this company email already exists");
//    }
//
//    // ---- 3) UserType PARTNER (PENDING) ----
//    Optional<UserType> existingPartnerType =
//            userTypeRepository.findByUserUserIdAndPtype(userId, "PARTNER");
//
//    if (existingPartnerType.isEmpty()) {
//        UserType ut = new UserType();
//        ut.setUser(user);
//        ut.setPtype("PARTNER");
//        ut.setStype(null);
//        ut.setIsapproved("PENDING");
//        ut.setCreatedAt(LocalDateTime.now());
//        userTypeRepository.save(ut);
//        log.info("[PARTNER-SELF-REGISTER] UserType PARTNER created (PENDING) for userId={}", userId);
//    } else {
//        if ("APPROVED".equalsIgnoreCase(existingPartnerType.get().getIsapproved())) {
//            throw new RuntimeException("You are already an approved partner");
//        }
//        log.info("[PARTNER-SELF-REGISTER] UserType PARTNER already exists (isapproved={}) for userId={}",
//                existingPartnerType.get().getIsapproved(), userId);
//    }
//
//    // ---- 4) PartnerDetails (upsert) ----
//    PartnerDetails partner = partnerRepository.findByUserUserId(userId)
//            .orElseGet(() -> {
//                PartnerDetails p = new PartnerDetails();
//                p.setUser(user);
//                p.setPartnerCode(generatePartnerCode());
//                return p;
//            });
//
//    if ("APPROVED".equalsIgnoreCase(partner.getPartnerStatus())) {
//        throw new RuntimeException("Partner account is already approved");
//    }
//
//    partner.setCompanyName(req.getCompanyName());
//    partner.setCompanyEmail(req.getCompanyEmail());
//    partner.setCompanyMobileNumber(req.getCompanyMobileNumber());
//    partner.setCompanyWebsite(req.getCompanyWebsite());
//    partner.setCompanyAddress(req.getCompanyAddress());
//    partner.setContactPersonName(req.getContactPersonName());
//    partner.setContactPersonDesignation(req.getContactPersonDesignation());
//
//    if (req.getServicesOffered() != null) {
//        partner.setServicesOffered(
//                req.getServicesOffered().stream()
//                        .filter(Objects::nonNull)
//                        .map(String::toUpperCase)
//                        .collect(Collectors.toSet())
//        );
//    }
//
//    partner.setAgreementType(agreementType);
//    partner.setAgreementValue(req.getAgreementValue());
//    partner.setAgreementPdfDriveLink(req.getAgreementPdfDriveLink());
//    partner.setAgreementStartDate(req.getAgreementStartDate());
//    partner.setAgreementEndDate(req.getAgreementEndDate());
//    partner.setInternalNotes(req.getInternalNotes());
//    partner.setPartnerStatus("PENDING");
//    partner.setIsApproved(false);
//
//    partner = partnerRepository.save(partner);
//    log.info("[PARTNER-SELF-REGISTER] PartnerDetails saved → partnerId={}, code={}",
//            partner.getPartnerId(), partner.getPartnerCode());
//
//    // ---- 5) Welcome email ----
//    sendPartnerWelcomeEmail(partner);
//
//    return toPartnerDTO(partner);
//}
//    // =====================================================================
//    // 2. HEAD DIRECTOR — approve / block / deactivate
//    // =====================================================================
//
//    @Transactional
//    public PartnerResponseDTO updatePartnerStatus(Long headDirectorUserId,
//                                                  Long partnerId,
//                                                  String newStatus,
//                                                  String note) {
//
//        validateHeadDirector(headDirectorUserId);
//
//        if (isBlank(newStatus) || !VALID_PARTNER_STATUSES.contains(newStatus.toUpperCase()))
//            throw new RuntimeException("Invalid partner status. Allowed: " + VALID_PARTNER_STATUSES);
//
//        final String status = newStatus.toUpperCase();
//
//        PartnerDetails partner = partnerRepository.findById(partnerId)
//                .orElseThrow(() -> new RuntimeException("Partner not found"));
//
//        partner.setPartnerStatus(status);
//        partner.setDirectorNote(note);
//        partner.setIsApproved("APPROVED".equals(status));
//
//        userTypeRepository.findByUserUserIdAndPtype(partner.getUser().getUserId(), "PARTNER")
//                .ifPresent(ut -> {
//                    ut.setIsapproved(status);
//                    userTypeRepository.save(ut);
//                });
//
//        partner = partnerRepository.save(partner);
//        log.info("[PARTNER-STATUS] partnerId={} → {} by headDirector={}",
//                partnerId, status, headDirectorUserId);
//
//        sendPartnerStatusEmail(partner, status, note);
//
//        return toPartnerDTO(partner);
//    }
//
//    // =====================================================================
//    // 3. HEAD DIRECTOR — list / view
//    // =====================================================================
//
//    public List<PartnerResponseDTO> getAllPartners(Long headDirectorUserId) {
//        validateHeadDirector(headDirectorUserId);
//        return partnerRepository.findAllOrderByCreatedAtDesc()
//                .stream().map(this::toPartnerDTO).collect(Collectors.toList());
//    }
//
//    public PartnerResponseDTO getPartnerById(Long headDirectorUserId, Long partnerId) {
//        validateHeadDirector(headDirectorUserId);
//        return partnerRepository.findById(partnerId)
//                .map(this::toPartnerDTO)
//                .orElseThrow(() -> new RuntimeException("Partner not found"));
//    }
//
//    // =====================================================================
//    // 4. PARTNER self — profile + dashboard
//    // =====================================================================
//
//    public PartnerResponseDTO getMyProfile(Long userId) {
//        return toPartnerDTO(requirePartner(userId));
//    }
//
//    public PartnerDashboardDTO getMyDashboard(Long userId) {
//        PartnerDetails partner = requirePartner(userId);
//
//        List<PartnerClientMapping> mappings =
//                mappingRepository.findActiveWithClientByPartnerId(partner.getPartnerId());
//
//        long totalClients = mappings.size();
//        long approvedClients = mappings.stream()
//                .filter(m -> Boolean.TRUE.equals(m.getClient().getIsApproved())).count();
//        long pendingClients = totalClients - approvedClients;
//
//        long totalProjects = 0, activeProjects = 0, completedProjects = 0;
//        long totalPayments = 0;
//        BigDecimal approvedRevenue = BigDecimal.ZERO;
//        BigDecimal pendingRevenue = BigDecimal.ZERO;
//
//        for (PartnerClientMapping m : mappings) {
//            Long clientUserId = m.getClient().getUser().getUserId();
//
//            List<ProjectDetails> projects = projectRepository.findByUploaderUserId(clientUserId);
//            totalProjects += projects.size();
//            activeProjects += projects.stream()
//                    .filter(p -> "IN_PROGRESS".equals(p.getStatus())).count();
//            completedProjects += projects.stream()
//                    .filter(p -> "COMPLETED".equals(p.getStatus())).count();
//
//            List<PaymentDetails> payments = paymentDetailsRepository.findByUserUserId(clientUserId);
//            totalPayments += payments.size();
//            for (PaymentDetails pay : payments) {
//                BigDecimal amt = parseAmount(pay.getAmount());
//                if (amt == null) continue;
//                if ("APPROVED".equalsIgnoreCase(pay.getStatus())) approvedRevenue = approvedRevenue.add(amt);
//                else if ("PENDING".equalsIgnoreCase(pay.getStatus())) pendingRevenue = pendingRevenue.add(amt);
//            }
//        }
//
//        List<PartnerClientResponseDTO> recentClients = mappings.stream()
//                .sorted((a, b) -> {
//                    if (a.getAssignedAt() == null) return 1;
//                    if (b.getAssignedAt() == null) return -1;
//                    return b.getAssignedAt().compareTo(a.getAssignedAt());
//                })
//                .limit(5)
//                .map(this::toClientDTO)
//                .collect(Collectors.toList());
//
//        return PartnerDashboardDTO.builder()
//                .partnerId(partner.getPartnerId())
//                .partnerCode(partner.getPartnerCode())
//                .companyName(partner.getCompanyName())
//                .partnerStatus(partner.getPartnerStatus())
//                .totalClients(totalClients)
//                .approvedClients(approvedClients)
//                .pendingClients(pendingClients)
//                .totalProjects(totalProjects)
//                .activeProjects(activeProjects)
//                .completedProjects(completedProjects)
//                .totalPaymentsCount(totalPayments)
//                .totalApprovedRevenue(approvedRevenue.doubleValue())
//                .totalPendingRevenue(pendingRevenue.doubleValue())
//                .recentClients(recentClients)
//                .build();
//    }
//
//    // =====================================================================
//    // 5. PARTNER — own clients
//    // =====================================================================
//
//    public List<PartnerClientResponseDTO> getMyClients(Long userId) {
//        PartnerDetails partner = requirePartner(userId);
//        return mappingRepository.findActiveWithClientByPartnerId(partner.getPartnerId())
//                .stream().map(this::toClientDTO).collect(Collectors.toList());
//    }
//
//    public PartnerClientResponseDTO getMyClientDetails(Long userId, Long clientId) {
//        PartnerDetails partner = requirePartner(userId);
//        assertClientBelongsToPartner(partner, clientId);
//
//        PartnerClientMapping mapping = mappingRepository
//                .findByPartnerPartnerIdAndClientId(partner.getPartnerId(), clientId)
//                .orElseThrow(() -> new RuntimeException("Mapping not found"));
//        return toClientDTO(mapping);
//    }
//
//    // =====================================================================
//    // 6. PARTNER — create a CLIENT (only if APPROVED)
//    // =====================================================================
//
//    @Transactional
//    public PartnerClientResponseDTO createClientForMe(Long userId, PartnerCreateClientRequest req) {
//
//        PartnerDetails partner = requireApprovedPartner(userId);
//
//        if (isBlank(req.getEmail()))       throw new RuntimeException("Client email is required");
//        if (isBlank(req.getCompanyName())) throw new RuntimeException("Company name is required");
//
//        // ---- 1) Existing user check ----
//        Optional<User> existingUser = userRepository.findByEmail(req.getEmail());
//
//        User clientUser;
//        if (existingUser.isPresent()) {
//            clientUser = existingUser.get();
//            Optional<ClientDetails> existingClient =
//                    clientDetailsRepository.findByUserUserId(clientUser.getUserId());
//            if (existingClient.isPresent()) {
//                if (mappingRepository.existsByPartnerPartnerIdAndClientId(
//                        partner.getPartnerId(), existingClient.get().getId())) {
//                    throw new RuntimeException("Client already mapped to your account");
//                }
//                throw new RuntimeException(
//                        "A client with this email already exists in the system. Please contact admin.");
//            }
//        } else {
//            clientUser = new User();
//            clientUser.setUid("PARTNER_CREATED_" + UUID.randomUUID());
//            clientUser.setEmail(req.getEmail());
//            clientUser.setEmailVerified(false);
//            clientUser.setIsProfileCompleted(false);
//            if (!isBlank(req.getName()))         clientUser.setName(req.getName());
//            if (!isBlank(req.getPhoneNumber()))  clientUser.setPhoneNumber(req.getPhoneNumber());
//            if (!isBlank(req.getGender()))       clientUser.setGender(req.getGender());
//            if (!isBlank(req.getAddress()))      clientUser.setAddress(req.getAddress());
//            if (!isBlank(req.getPhotoUrl()))     clientUser.setPhotoUrl(req.getPhotoUrl());
//            if (!isBlank(req.getDob())) {
//                try { clientUser.setDob(LocalDate.parse(req.getDob())); } catch (Exception ignored) {}
//            }
//        }
//
//        // ✅ FIX: final variable
//        final User savedClientUser = userRepository.save(clientUser);
//
//        // ---- 2) UserType CLIENT (PENDING) ----
//        if (userTypeRepository.findByUserUserIdAndPtype(savedClientUser.getUserId(), "CLIENT").isEmpty()) {
//            UserType ut = new UserType();
//            ut.setUser(savedClientUser);
//            ut.setPtype("CLIENT");
//            ut.setStype(null);
//            ut.setIsapproved("PENDING");
//            ut.setCreatedAt(LocalDateTime.now());
//            userTypeRepository.save(ut);
//        }
//
//        // ---- 3) ClientDetails ----
//        ClientDetails client = clientDetailsRepository.findByUserUserId(savedClientUser.getUserId())
//                .orElseGet(() -> {
//                    ClientDetails c = new ClientDetails();
//                    c.setUser(savedClientUser);      // ✅ effectively final
//                    return c;
//                });
//
//        client.setCompanyName(req.getCompanyName());
//        client.setCompanyEmail(req.getCompanyEmail());
//        client.setCompanyMobileNumber(req.getCompanyMobileNumber());
//        client.setOrgType(req.getOrgType());
//        client.setClientType(req.getClientType());
//        client.setClientWebsite(req.getClientWebsite());
//        if (client.getIsApproved() == null) client.setIsApproved(false);
//        if (client.getClientStatus() == null) client.setClientStatus("PENDING");
//        client = clientDetailsRepository.save(client);
//
//        // ---- 4) Mapping ----
//        PartnerClientMapping mapping = new PartnerClientMapping();
//        mapping.setPartner(partner);
//        mapping.setClient(client);
//        mapping.setAssignedAt(LocalDateTime.now());
//        mapping.setCreatedBy(userRepository.findById(userId).orElse(null));
//        mapping.setMappingStatus("ACTIVE");
//        mapping.setNotes(req.getNotes());
//        mapping = mappingRepository.save(mapping);
//
//        log.info("[PARTNER-CLIENT] partnerId={} created clientId={} (email={})",
//                partner.getPartnerId(), client.getId(), savedClientUser.getEmail());
//
//        // ---- 5) Welcome email ----
//        sendClientWelcomeEmail(savedClientUser, client, partner);
//
//        return toClientDTO(mapping);
//    }
//
//    // =====================================================================
//    // 7. PARTNER — view own client's projects / payments
//    // =====================================================================
//
//    public List<PartnerProjectSummaryDTO> getMyClientProjects(Long userId, Long clientId) {
//        PartnerDetails partner = requireApprovedPartner(userId);
//        assertClientBelongsToPartner(partner, clientId);
//
//        ClientDetails client = clientDetailsRepository.findById(clientId)
//                .orElseThrow(() -> new RuntimeException("Client not found"));
//
//        return projectRepository.findByUploaderUserId(client.getUser().getUserId())
//                .stream().map(this::toProjectSummary).collect(Collectors.toList());
//    }
//
//    public List<PaymentDetails> getMyClientPayments(Long userId, Long clientId) {
//        PartnerDetails partner = requireApprovedPartner(userId);
//        assertClientBelongsToPartner(partner, clientId);
//
//        ClientDetails client = clientDetailsRepository.findById(clientId)
//                .orElseThrow(() -> new RuntimeException("Client not found"));
//        return paymentDetailsRepository.findByUserUserId(client.getUser().getUserId());
//    }
//
//    // =====================================================================
//    // 8. PARTNER — create project for own client
//    // =====================================================================
//
//    @Transactional
//    public PartnerProjectSummaryDTO createProjectForClient(Long userId,
//                                                           Long clientId,
//                                                           PartnerCreateProjectRequest req) {
//
//        PartnerDetails partner = requireApprovedPartner(userId);
//        assertClientBelongsToPartner(partner, clientId);
//
//        if (isBlank(req.getProjectName()))
//            throw new RuntimeException("Project name is required");
//
//        ClientDetails client = clientDetailsRepository.findById(clientId)
//                .orElseThrow(() -> new RuntimeException("Client not found"));
//
//        ProjectDetails project = new ProjectDetails();
//        project.setProjectName(req.getProjectName());
//        project.setProjectDescription(req.getProjectDescription());
//        project.setProjectType(req.getProjectType());
//        project.setCost(req.getCost());
//        project.setDeadline(req.getDeadline());
//        project.setUploader(client.getUser());
//        project.setStatus("PENDING");
//
//        if (req.getDepartmentId() != null) {
//            Department dept = departmentRepository.findById(req.getDepartmentId())
//                    .orElseThrow(() -> new RuntimeException("Department not found"));
//            project.setDepartment(dept);
//        }
//
//        project = projectRepository.save(project);
//        log.info("[PARTNER-PROJECT] partnerId={} created projectId={} for clientId={}",
//                partner.getPartnerId(), project.getProjectId(), clientId);
//
//        sendProjectCreatedEmail(client, project, partner);
//
//        return toProjectSummary(project);
//    }
//
//    // =====================================================================
//    // EMAIL HELPERS
//    // =====================================================================
//
//    private void sendPartnerWelcomeEmail(PartnerDetails partner) {
//        try {
//            User u = partner.getUser();
//            if (isBlank(u.getEmail())) return;
//
//            String subject = "🤝 Partner Registration Received - Softmax Technologies";
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;">
//                <div style="max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);">
//                    <div style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);padding:40px 30px;text-align:center;">
//                        <h1 style="color:#fff;margin:0;font-size:26px;">🤝 Welcome to Partner Program</h1>
//                        <p style="color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;">Softmax Technologies and IT Services</p>
//                    </div>
//                    <div style="padding:40px 35px;">
//                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                        <p style="color:#4a5568;font-size:15px;">Thank you for registering as a partner. Your application for <strong>%s</strong> has been received successfully.</p>
//                        <div style="background:#f0f4ff;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #667eea;">
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🔖 <strong>Partner Code:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🏢 <strong>Company:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📧 <strong>Email:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📊 <strong>Status:</strong> <span style="background:#fff9e6;color:#b8860b;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600;">⏳ PENDING APPROVAL</span></p>
//                        </div>
//                        <p style="color:#4a5568;">Our Head Director will review your agreement and approve your account shortly. You will be able to create clients once approved.</p>
//                        <p style="color:#4a5568;font-size:13px;margin-top:24px;">Received on: %s</p>
//                    </div>
//                    <div style="background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;">
//                        <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
//                        <p style="margin:6px 0;">🌐 <a href="https://softmax.in" style="color:#667eea;">softmax.in</a> | 💼 <a href="https://nexus.softmax.in" style="color:#667eea;">nexus.softmax.in</a></p>
//                        <p style="margin-top:12px;">© 2026 Softmax Technologies - All Rights Reserved</p>
//                    </div>
//                </div></body></html>
//                """,
//                    safe(u.getName()),
//                    safe(partner.getCompanyName()),
//                    partner.getPartnerCode(),
//                    safe(partner.getCompanyName()),
//                    safe(u.getEmail()),
//                    LocalDateTime.now().format(emailDateFmt)
//            );
//            emailService.sendEmail(u.getEmail(), subject, html);
//            log.info("[PARTNER-EMAIL] Welcome email sent → {}", u.getEmail());
//        } catch (Exception e) {
//            log.warn("[PARTNER-EMAIL] Welcome email failed: {}", e.getMessage());
//        }
//    }
//
//    private void sendPartnerStatusEmail(PartnerDetails partner, String status, String note) {
//        try {
//            User u = partner.getUser();
//            if (isBlank(u.getEmail())) return;
//
//            String icon, color, message;
//            switch (status) {
//                case "APPROVED":
//                    icon = "✅"; color = "#11998e";
//                    message = "Your partner account has been <strong>APPROVED</strong>. You can now create and manage clients.";
//                    break;
//                case "BLOCKED":
//                    icon = "🚫"; color = "#dc2626";
//                    message = "Your partner account has been <strong>BLOCKED</strong>. Please contact support for details.";
//                    break;
//                case "DEACTIVATED":
//                    icon = "⏸️"; color = "#6b7280";
//                    message = "Your partner account has been <strong>DEACTIVATED</strong>. Please contact support to reactivate.";
//                    break;
//                default:
//                    icon = "⏳"; color = "#f59e0b";
//                    message = "Your partner account status has been updated to <strong>" + status + "</strong>.";
//            }
//
//            String subject = icon + " Partner Account " + status + " - Softmax";
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;">
//                <div style="max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);">
//                    <div style="background:%s;padding:40px 30px;text-align:center;">
//                        <h1 style="color:#fff;margin:0;font-size:26px;">%s Partner Account Update</h1>
//                        <p style="color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;">Softmax Technologies</p>
//                    </div>
//                    <div style="padding:40px 35px;">
//                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                        <p style="color:#4a5568;font-size:15px;">%s</p>
//                        <div style="background:#f8f9fa;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid %s;">
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🔖 <strong>Partner Code:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🏢 <strong>Company:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📊 <strong>New Status:</strong> %s</p>
//                            %s
//                        </div>
//                        <p style="color:#4a5568;">Login to <a href="https://nexus.softmax.in" style="color:#667eea;">nexus.softmax.in</a> for more details.</p>
//                    </div>
//                    <div style="background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;">
//                        <p>© 2026 Softmax Technologies - All Rights Reserved</p>
//                    </div>
//                </div></body></html>
//                """,
//                    color, icon,
//                    safe(u.getName()),
//                    message,
//                    color,
//                    partner.getPartnerCode(),
//                    safe(partner.getCompanyName()),
//                    status,
//                    note != null ? "<p style=\"margin:8px 0;color:#2d3748;font-size:14px;\">📝 <strong>Note:</strong> " + safe(note) + "</p>" : ""
//            );
//            emailService.sendEmail(u.getEmail(), subject, html);
//            log.info("[PARTNER-EMAIL] Status email ({}) sent → {}", status, u.getEmail());
//        } catch (Exception e) {
//            log.warn("[PARTNER-EMAIL] Status email failed: {}", e.getMessage());
//        }
//    }
//
//    private void sendClientWelcomeEmail(User clientUser, ClientDetails client, PartnerDetails partner) {
//        try {
//            if (clientUser == null || isBlank(clientUser.getEmail())) return;
//
//            String subject = "🏢 Welcome to Softmax - Your Client Account is Ready";
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;">
//                <div style="max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);">
//                    <div style="background:linear-gradient(135deg,#2196F3 0%%,#1a237e 100%%);padding:40px 30px;text-align:center;">
//                        <h1 style="color:#fff;margin:0;font-size:26px;">🏢 Welcome to Softmax</h1>
//                        <p style="color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;">Your client account is ready</p>
//                    </div>
//                    <div style="padding:40px 35px;">
//                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                        <p style="color:#4a5568;font-size:15px;">Your client account has been created for <strong>%s</strong> via our partner <strong>%s</strong>.</p>
//                        <div style="background:#e3f2fd;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #2196F3;">
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🏢 <strong>Company:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📧 <strong>Email:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🤝 <strong>Brought by Partner:</strong> %s</p>
//                        </div>
//                        <p style="color:#4a5568;"><strong>Next step:</strong> Sign up at <a href="https://nexus.softmax.in" style="color:#2196F3;">nexus.softmax.in</a> using your email <strong>%s</strong> to access your dashboard.</p>
//                        <div style="text-align:center;margin-top:24px;">
//                            <a href="https://nexus.softmax.in" style="display:inline-block;background:linear-gradient(135deg,#2196F3,#1a237e);color:#fff;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;">🚀 Go to Dashboard</a>
//                        </div>
//                    </div>
//                    <div style="background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;">
//                        <p>© 2026 Softmax Technologies - All Rights Reserved</p>
//                    </div>
//                </div></body></html>
//                """,
//                    safe(clientUser.getName()),
//                    safe(client.getCompanyName()),
//                    safe(partner.getCompanyName()),
//                    safe(client.getCompanyName()),
//                    safe(clientUser.getEmail()),
//                    safe(partner.getCompanyName()),
//                    safe(clientUser.getEmail())
//            );
//            emailService.sendEmail(clientUser.getEmail(), subject, html);
//            log.info("[PARTNER-EMAIL] Client welcome email sent → {}", clientUser.getEmail());
//        } catch (Exception e) {
//            log.warn("[PARTNER-EMAIL] Client welcome email failed: {}", e.getMessage());
//        }
//    }
//
//    private void sendProjectCreatedEmail(ClientDetails client, ProjectDetails project, PartnerDetails partner) {
//        try {
//            User clientUser = client.getUser();
//            if (clientUser == null || isBlank(clientUser.getEmail())) return;
//
//            String subject = "📁 New Project Created: " + safe(project.getProjectName()) + " - Softmax";
//            String html = String.format("""
//                <!DOCTYPE html>
//                <html><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;">
//                <div style="max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);">
//                    <div style="background:linear-gradient(135deg,#11998e 0%%,#38ef7d 100%%);padding:40px 30px;text-align:center;">
//                        <h1 style="color:#fff;margin:0;font-size:26px;">📁 New Project Created</h1>
//                        <p style="color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;">Softmax Technologies</p>
//                    </div>
//                    <div style="padding:40px 35px;">
//                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
//                        <p style="color:#4a5568;font-size:15px;">A new project has been created for your account by our partner <strong>%s</strong>.</p>
//                        <div style="background:#e8f5e9;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #11998e;">
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📁 <strong>Project:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📝 <strong>Type:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">💰 <strong>Cost:</strong> %s</p>
//                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📊 <strong>Status:</strong> %s</p>
//                        </div>
//                        <p style="color:#4a5568;">Login to your dashboard for more details.</p>
//                    </div>
//                    <div style="background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;">
//                        <p>© 2026 Softmax Technologies - All Rights Reserved</p>
//                    </div>
//                </div></body></html>
//                """,
//                    safe(clientUser.getName()),
//                    safe(partner.getCompanyName()),
//                    safe(project.getProjectName()),
//                    project.getProjectType() != null ? safe(project.getProjectType()) : "Not specified",
//                    project.getCost() != null ? safe(project.getCost()) : "TBD",
//                    safe(project.getStatus())
//            );
//            emailService.sendEmail(clientUser.getEmail(), subject, html);
//            log.info("[PARTNER-EMAIL] Project created email sent → {}", clientUser.getEmail());
//        } catch (Exception e) {
//            log.warn("[PARTNER-EMAIL] Project email failed: {}", e.getMessage());
//        }
//    }
//
//    // =====================================================================
//    // GUARDS
//    // =====================================================================
//
//    private PartnerDetails requirePartner(Long userId) {
//        return partnerRepository.findByUserUserId(userId)
//                .orElseThrow(() -> new RuntimeException("Access denied: Partner profile not found"));
//    }
//
//    private PartnerDetails requireApprovedPartner(Long userId) {
//        PartnerDetails p = requirePartner(userId);
//        if (!"APPROVED".equalsIgnoreCase(p.getPartnerStatus())
//                || !Boolean.TRUE.equals(p.getIsApproved())) {
//            throw new RuntimeException(
//                    "Access denied: Partner account is not APPROVED (current: "
//                            + p.getPartnerStatus() + "). Please wait for Head Director approval.");
//        }
//        return p;
//    }
//
//    private void assertClientBelongsToPartner(PartnerDetails partner, Long clientId) {
//        boolean owned = mappingRepository.isClientOwnedByPartner(partner.getPartnerId(), clientId);
//        if (!owned) {
//            throw new RuntimeException("Access denied: This client does not belong to you");
//        }
//    }
//
//    private boolean isHeadDirector(Long userId) {
//        Optional<EmployeeDetails> emp = employeeDetailsRepository.findByUserUserId(userId);
//        if (emp.isEmpty()) return false;
//        boolean isDirector = userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
//                .map(ut -> "DIRECTOR".equals(ut.getStype())).orElse(false);
//        return isDirector && emp.get().getDepartmentRef() != null
//                && HEAD_DEPARTMENT_ID.equals(emp.get().getDepartmentRef().getDepartmentId());
//    }
//
//    private void validateHeadDirector(Long userId) {
//        if (!isHeadDirector(userId))
//            throw new RuntimeException("Access denied: Only Head Director can perform this action");
//    }
//
//    // =====================================================================
//    // MAPPERS & UTILS
//    // =====================================================================
//
//    private PartnerResponseDTO toPartnerDTO(PartnerDetails p) {
//        long totalClients = mappingRepository.countByPartnerPartnerId(p.getPartnerId());
//        long activeClients = mappingRepository
//                .countByPartnerPartnerIdAndMappingStatus(p.getPartnerId(), "ACTIVE");
//
//        return PartnerResponseDTO.builder()
//                .partnerId(p.getPartnerId())
//                .userId(p.getUser().getUserId())
//                .partnerCode(p.getPartnerCode())
//                .partnerName(p.getUser().getName())
//                .partnerEmail(p.getUser().getEmail())
//                .partnerPhotoUrl(p.getUser().getPhotoUrl())
//                .companyName(p.getCompanyName())
//                .companyEmail(p.getCompanyEmail())
//                .companyMobileNumber(p.getCompanyMobileNumber())
//                .companyWebsite(p.getCompanyWebsite())
//                .companyAddress(p.getCompanyAddress())
//                .contactPersonName(p.getContactPersonName())
//                .contactPersonDesignation(p.getContactPersonDesignation())
//                .servicesOffered(p.getServicesOffered())
//                .agreementType(p.getAgreementType())
//                .agreementValue(p.getAgreementValue())
//                .agreementPdfDriveLink(p.getAgreementPdfDriveLink())
//                .agreementStartDate(p.getAgreementStartDate())
//                .agreementEndDate(p.getAgreementEndDate())
//                .partnerStatus(p.getPartnerStatus())
//                .isApproved(p.getIsApproved())
//                .directorNote(p.getDirectorNote())
//                .totalClients(totalClients)
//                .activeClients(activeClients)
//                .createdAt(p.getCreatedAt())
//                .updatedAt(p.getUpdatedAt())
//                .build();
//    }
//
//    private PartnerClientResponseDTO toClientDTO(PartnerClientMapping m) {
//        ClientDetails c = m.getClient();
//        User u = c.getUser();
//        return PartnerClientResponseDTO.builder()
//                .mappingId(m.getMappingId())
//                .clientId(c.getId())
//                .userId(u.getUserId())
//                .name(u.getName())
//                .email(u.getEmail())
//                .phoneNumber(u.getPhoneNumber())
//                .companyName(c.getCompanyName())
//                .companyEmail(c.getCompanyEmail())
//                .clientStatus(c.getClientStatus())
//                .isApproved(c.getIsApproved())
//                .assignedAt(m.getAssignedAt())
//                .mappingStatus(m.getMappingStatus())
//                .notes(m.getNotes())
//                .build();
//    }
//
//    private PartnerProjectSummaryDTO toProjectSummary(ProjectDetails p) {
//        return PartnerProjectSummaryDTO.builder()
//                .projectId(p.getProjectId())
//                .projectName(p.getProjectName())
//                .projectDescription(p.getProjectDescription())
//                .projectType(p.getProjectType())
//                .status(p.getStatus())
//                .cost(p.getCost())
//                .deadline(p.getDeadline())
//                .departmentId(p.getDepartment() != null ? p.getDepartment().getDepartmentId() : null)
//                .departmentName(p.getDepartment() != null ? p.getDepartment().getDepartmentName() : null)
//                .createdAt(p.getCreatedAt())
//                .updatedAt(p.getUpdatedAt())
//                .build();
//    }
//
//    private String generatePartnerCode() {
//        String code;
//        do {
//            code = "PTR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
//        } while (partnerRepository.existsByPartnerCode(code));
//        return code;
//    }
//
//    private BigDecimal parseAmount(String amt) {
//        if (amt == null || amt.isBlank()) return null;
//        try { return new BigDecimal(amt.trim()); } catch (Exception e) { return null; }
//    }
//
//    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
//
//    private String safe(String s) {
//        if (s == null) return "";
//        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
//    }
//}

package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.PartnerCreateClientRequest;
import com.softmax.shardaos.dto.input.PartnerCreateProjectRequest;
import com.softmax.shardaos.dto.input.PartnerSelfRegisterRequest;
import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PartnerManagementService {

    private static final Logger log = LoggerFactory.getLogger(PartnerManagementService.class);

    private static final Set<String> VALID_AGREEMENT_TYPES = Set.of("PERCENTAGE", "MONTHLY", "FIXED");
    private static final Set<String> VALID_SERVICES = Set.of(
            "CLIENT_RELATIONSHIP", "SELLING", "SECURITY",
            "MARKETING", "SUPPORT", "DEVELOPMENT", "OTHER"
    );
    private static final Set<String> VALID_PARTNER_STATUSES =
            Set.of("PENDING", "APPROVED", "BLOCKED", "DEACTIVATED");

    private static final Long HEAD_DEPARTMENT_ID = 1L;

    private final DateTimeFormatter emailDateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    @Autowired private PartnerDetailsRepository partnerRepository;
    @Autowired private PartnerClientMappingRepository mappingRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserTypeRepository userTypeRepository;
    @Autowired private ClientDetailsRepository clientDetailsRepository;
    @Autowired private ProjectDetailsRepository projectRepository;
    @Autowired private PaymentDetailsRepository paymentDetailsRepository;
    @Autowired private EmployeeDetailsRepository employeeDetailsRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private ProjectStatusRepository projectStatusRepository;
    @Autowired private ProjectPaymentRepository projectPaymentRepository;
    @Autowired private EmailService emailService;

    // =====================================================================
    // 1. SELF REGISTRATION (JWT — user must be signed in)
    // =====================================================================

    @Transactional
    public PartnerResponseDTO selfRegister(Long userId, PartnerSelfRegisterRequest req) {

        log.info("[PARTNER-SELF-REGISTER] userId={}, company={}", userId, req.getCompanyName());

        // ---- 1) User must already exist (JWT se aaya) ----
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found. Please sign in first."));

        // ---- 2) Validation ----
        if (isBlank(req.getCompanyName()))
            throw new RuntimeException("Company name is required");
        if (isBlank(req.getAgreementPdfDriveLink()))
            throw new RuntimeException("Agreement PDF drive link is required");
        if (isBlank(req.getAgreementType()))
            throw new RuntimeException("Agreement type is required");

        final String agreementType = req.getAgreementType().toUpperCase();
        if (!VALID_AGREEMENT_TYPES.contains(agreementType))
            throw new RuntimeException("Invalid agreementType. Allowed: " + VALID_AGREEMENT_TYPES);

        if (req.getServicesOffered() != null) {
            for (String s : req.getServicesOffered()) {
                if (s == null) continue;
                if (!VALID_SERVICES.contains(s.toUpperCase()))
                    throw new RuntimeException("Invalid service: " + s + ". Allowed: " + VALID_SERVICES);
            }
        }

        if (!isBlank(req.getCompanyEmail())
                && partnerRepository.existsByCompanyEmail(req.getCompanyEmail())
                && !req.getCompanyEmail().equalsIgnoreCase(user.getEmail())) {
            throw new RuntimeException("A partner with this company email already exists");
        }

        // ---- 3) UserType PARTNER (PENDING) ----
        Optional<UserType> existingPartnerType =
                userTypeRepository.findByUserUserIdAndPtype(userId, "PARTNER");

        if (existingPartnerType.isEmpty()) {
            UserType ut = new UserType();
            ut.setUser(user);
            ut.setPtype("PARTNER");
            ut.setStype(null);
            ut.setIsapproved("PENDING");
            ut.setCreatedAt(LocalDateTime.now());
            userTypeRepository.save(ut);
            log.info("[PARTNER-SELF-REGISTER] UserType PARTNER created (PENDING) for userId={}", userId);
        } else {
            if ("APPROVED".equalsIgnoreCase(existingPartnerType.get().getIsapproved())) {
                throw new RuntimeException("You are already an approved partner");
            }
            log.info("[PARTNER-SELF-REGISTER] UserType PARTNER already exists (isapproved={}) for userId={}",
                    existingPartnerType.get().getIsapproved(), userId);
        }

        // ---- 4) PartnerDetails (upsert) ----
        PartnerDetails partner = partnerRepository.findByUserUserId(userId)
                .orElseGet(() -> {
                    PartnerDetails p = new PartnerDetails();
                    p.setUser(user);
                    p.setPartnerCode(generatePartnerCode());
                    return p;
                });

        if ("APPROVED".equalsIgnoreCase(partner.getPartnerStatus())) {
            throw new RuntimeException("Partner account is already approved");
        }

        partner.setCompanyName(req.getCompanyName());
        partner.setCompanyEmail(req.getCompanyEmail());
        partner.setCompanyMobileNumber(req.getCompanyMobileNumber());
        partner.setCompanyWebsite(req.getCompanyWebsite());
        partner.setCompanyAddress(req.getCompanyAddress());
        partner.setContactPersonName(req.getContactPersonName());
        partner.setContactPersonDesignation(req.getContactPersonDesignation());

        if (req.getServicesOffered() != null) {
            partner.setServicesOffered(
                    req.getServicesOffered().stream()
                            .filter(Objects::nonNull)
                            .map(String::toUpperCase)
                            .collect(Collectors.toSet())
            );
        }

        partner.setAgreementType(agreementType);
        partner.setAgreementValue(req.getAgreementValue());
        partner.setAgreementPdfDriveLink(req.getAgreementPdfDriveLink());
        partner.setAgreementStartDate(req.getAgreementStartDate());
        partner.setAgreementEndDate(req.getAgreementEndDate());
        partner.setInternalNotes(req.getInternalNotes());
        partner.setPartnerStatus("PENDING");
        partner.setIsApproved(false);

        partner = partnerRepository.save(partner);
        log.info("[PARTNER-SELF-REGISTER] PartnerDetails saved → partnerId={}, code={}",
                partner.getPartnerId(), partner.getPartnerCode());

        sendPartnerWelcomeEmail(partner);

        return toPartnerDTO(partner);
    }

    // =====================================================================
    // 2. HEAD DIRECTOR — approve / block / deactivate
    // =====================================================================

    @Transactional
    public PartnerResponseDTO updatePartnerStatus(Long headDirectorUserId,
                                                  Long partnerId,
                                                  String newStatus,
                                                  String note) {

        validateHeadDirector(headDirectorUserId);

        if (isBlank(newStatus) || !VALID_PARTNER_STATUSES.contains(newStatus.toUpperCase()))
            throw new RuntimeException("Invalid partner status. Allowed: " + VALID_PARTNER_STATUSES);

        final String status = newStatus.toUpperCase();

        PartnerDetails partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new RuntimeException("Partner not found"));

        partner.setPartnerStatus(status);
        partner.setDirectorNote(note);
        partner.setIsApproved("APPROVED".equals(status));

        userTypeRepository.findByUserUserIdAndPtype(partner.getUser().getUserId(), "PARTNER")
                .ifPresent(ut -> {
                    ut.setIsapproved(status);
                    userTypeRepository.save(ut);
                });

        partner = partnerRepository.save(partner);
        log.info("[PARTNER-STATUS] partnerId={} → {} by headDirector={}",
                partnerId, status, headDirectorUserId);

        sendPartnerStatusEmail(partner, status, note);

        return toPartnerDTO(partner);
    }

    // =====================================================================
    // 3. HEAD DIRECTOR — list / view
    // =====================================================================

    public List<PartnerResponseDTO> getAllPartners(Long headDirectorUserId) {
        validateHeadDirector(headDirectorUserId);
        return partnerRepository.findAllOrderByCreatedAtDesc()
                .stream().map(this::toPartnerDTO).collect(Collectors.toList());
    }

    public PartnerResponseDTO getPartnerById(Long headDirectorUserId, Long partnerId) {
        validateHeadDirector(headDirectorUserId);
        return partnerRepository.findById(partnerId)
                .map(this::toPartnerDTO)
                .orElseThrow(() -> new RuntimeException("Partner not found"));
    }

    // =====================================================================
    // 4. PARTNER self — profile + dashboard
    // =====================================================================

    public PartnerResponseDTO getMyProfile(Long userId) {
        return toPartnerDTO(requirePartner(userId));
    }

    public PartnerDashboardDTO getMyDashboard(Long userId) {
        PartnerDetails partner = requirePartner(userId);

        List<PartnerClientMapping> mappings =
                mappingRepository.findActiveWithClientByPartnerId(partner.getPartnerId());

        long totalClients = mappings.size();
        long approvedClients = mappings.stream()
                .filter(m -> Boolean.TRUE.equals(m.getClient().getIsApproved())).count();
        long pendingClients = totalClients - approvedClients;

        long totalProjects = 0, activeProjects = 0, completedProjects = 0;
        long totalPayments = 0;
        BigDecimal approvedRevenue = BigDecimal.ZERO;
        BigDecimal pendingRevenue = BigDecimal.ZERO;

        for (PartnerClientMapping m : mappings) {
            Long clientUserId = m.getClient().getUser().getUserId();

            List<ProjectDetails> projects = projectRepository.findByUploaderUserId(clientUserId);
            totalProjects += projects.size();
            activeProjects += projects.stream()
                    .filter(p -> "IN_PROGRESS".equals(p.getStatus())).count();
            completedProjects += projects.stream()
                    .filter(p -> "COMPLETED".equals(p.getStatus())).count();

            List<PaymentDetails> payments = paymentDetailsRepository.findByUserUserId(clientUserId);
            totalPayments += payments.size();
            for (PaymentDetails pay : payments) {
                BigDecimal amt = parseAmount(pay.getAmount());
                if (amt == null) continue;
                if ("APPROVED".equalsIgnoreCase(pay.getStatus())) approvedRevenue = approvedRevenue.add(amt);
                else if ("PENDING".equalsIgnoreCase(pay.getStatus())) pendingRevenue = pendingRevenue.add(amt);
            }
        }

        List<PartnerClientResponseDTO> recentClients = mappings.stream()
                .sorted((a, b) -> {
                    if (a.getAssignedAt() == null) return 1;
                    if (b.getAssignedAt() == null) return -1;
                    return b.getAssignedAt().compareTo(a.getAssignedAt());
                })
                .limit(5)
                .map(this::toClientDTO)
                .collect(Collectors.toList());

        return PartnerDashboardDTO.builder()
                .partnerId(partner.getPartnerId())
                .partnerCode(partner.getPartnerCode())
                .companyName(partner.getCompanyName())
                .partnerStatus(partner.getPartnerStatus())
                .totalClients(totalClients)
                .approvedClients(approvedClients)
                .pendingClients(pendingClients)
                .totalProjects(totalProjects)
                .activeProjects(activeProjects)
                .completedProjects(completedProjects)
                .totalPaymentsCount(totalPayments)
                .totalApprovedRevenue(approvedRevenue.doubleValue())
                .totalPendingRevenue(pendingRevenue.doubleValue())
                .recentClients(recentClients)
                .build();
    }

    // =====================================================================
    // 5. PARTNER — own clients
    // =====================================================================

    public List<PartnerClientResponseDTO> getMyClients(Long userId) {
        PartnerDetails partner = requirePartner(userId);
        return mappingRepository.findActiveWithClientByPartnerId(partner.getPartnerId())
                .stream().map(this::toClientDTO).collect(Collectors.toList());
    }

    public PartnerClientResponseDTO getMyClientDetails(Long userId, Long clientId) {
        PartnerDetails partner = requirePartner(userId);
        assertClientBelongsToPartner(partner, clientId);

        PartnerClientMapping mapping = mappingRepository
                .findByPartnerPartnerIdAndClientId(partner.getPartnerId(), clientId)
                .orElseThrow(() -> new RuntimeException("Mapping not found"));
        return toClientDTO(mapping);
    }

    // =====================================================================
    // 6. PARTNER — create a CLIENT (only if APPROVED)
    // =====================================================================

    @Transactional
    public PartnerClientResponseDTO createClientForMe(Long userId, PartnerCreateClientRequest req) {

        PartnerDetails partner = requireApprovedPartner(userId);

        if (isBlank(req.getEmail()))       throw new RuntimeException("Client email is required");
        if (isBlank(req.getCompanyName())) throw new RuntimeException("Company name is required");

        Optional<User> existingUser = userRepository.findByEmail(req.getEmail());

        User clientUser;
        if (existingUser.isPresent()) {
            clientUser = existingUser.get();
            Optional<ClientDetails> existingClient =
                    clientDetailsRepository.findByUserUserId(clientUser.getUserId());
            if (existingClient.isPresent()) {
                if (mappingRepository.existsByPartnerPartnerIdAndClientId(
                        partner.getPartnerId(), existingClient.get().getId())) {
                    throw new RuntimeException("Client already mapped to your account");
                }
                throw new RuntimeException(
                        "A client with this email already exists in the system. Please contact admin.");
            }
        } else {
            clientUser = new User();
            clientUser.setUid("PARTNER_CREATED_" + UUID.randomUUID());
            clientUser.setEmail(req.getEmail());
            clientUser.setEmailVerified(false);
            clientUser.setIsProfileCompleted(false);
            if (!isBlank(req.getName()))         clientUser.setName(req.getName());
            if (!isBlank(req.getPhoneNumber()))  clientUser.setPhoneNumber(req.getPhoneNumber());
            if (!isBlank(req.getGender()))       clientUser.setGender(req.getGender());
            if (!isBlank(req.getAddress()))      clientUser.setAddress(req.getAddress());
            if (!isBlank(req.getPhotoUrl()))     clientUser.setPhotoUrl(req.getPhotoUrl());
            if (!isBlank(req.getDob())) {
                try { clientUser.setDob(LocalDate.parse(req.getDob())); } catch (Exception ignored) {}
            }
        }

        final User savedClientUser = userRepository.save(clientUser);

        if (userTypeRepository.findByUserUserIdAndPtype(savedClientUser.getUserId(), "CLIENT").isEmpty()) {
            UserType ut = new UserType();
            ut.setUser(savedClientUser);
            ut.setPtype("CLIENT");
            ut.setStype(null);
            ut.setIsapproved("PENDING");
            ut.setCreatedAt(LocalDateTime.now());
            userTypeRepository.save(ut);
        }

        ClientDetails client = clientDetailsRepository.findByUserUserId(savedClientUser.getUserId())
                .orElseGet(() -> {
                    ClientDetails c = new ClientDetails();
                    c.setUser(savedClientUser);
                    return c;
                });

        client.setCompanyName(req.getCompanyName());
        client.setCompanyEmail(req.getCompanyEmail());
        client.setCompanyMobileNumber(req.getCompanyMobileNumber());
        client.setOrgType(req.getOrgType());
        client.setClientType(req.getClientType());
        client.setClientWebsite(req.getClientWebsite());
        if (client.getIsApproved() == null) client.setIsApproved(false);
        if (client.getClientStatus() == null) client.setClientStatus("PENDING");
        client = clientDetailsRepository.save(client);

        PartnerClientMapping mapping = new PartnerClientMapping();
        mapping.setPartner(partner);
        mapping.setClient(client);
        mapping.setAssignedAt(LocalDateTime.now());
        mapping.setCreatedBy(userRepository.findById(userId).orElse(null));
        mapping.setMappingStatus("ACTIVE");
        mapping.setNotes(req.getNotes());
        mapping = mappingRepository.save(mapping);

        log.info("[PARTNER-CLIENT] partnerId={} created clientId={} (email={})",
                partner.getPartnerId(), client.getId(), savedClientUser.getEmail());

        sendClientWelcomeEmail(savedClientUser, client, partner);

        return toClientDTO(mapping);
    }

    // =====================================================================
    // 7. PARTNER — view own client's projects / payments
    // =====================================================================

    public List<PartnerProjectSummaryDTO> getMyClientProjects(Long userId, Long clientId) {
        PartnerDetails partner = requireApprovedPartner(userId);
        assertClientBelongsToPartner(partner, clientId);

        ClientDetails client = clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return projectRepository.findByUploaderUserId(client.getUser().getUserId())
                .stream().map(this::toProjectSummary).collect(Collectors.toList());
    }

    public List<PaymentResponseDTO> getMyClientPayments(Long userId, Long clientId) {
        PartnerDetails partner = requireApprovedPartner(userId);
        assertClientBelongsToPartner(partner, clientId);

        ClientDetails client = clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return paymentDetailsRepository.findByUserUserId(client.getUser().getUserId())
                .stream().map(this::toPaymentResponseDTO).collect(Collectors.toList());
    }

    // =====================================================================
    // 7-B. PARTNER — full project detail with history + payments
    // =====================================================================

    /**
     * All projects for a specific client — WITH history + payments attached.
     */
    public List<ProjectPaymentSummaryDTO> getMyClientProjectsWithDetails(Long userId,
                                                                         Long clientId) {

        PartnerDetails partner = requireApprovedPartner(userId);
        assertClientBelongsToPartner(partner, clientId);

        ClientDetails client = clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        Long clientUserId = client.getUser().getUserId();
        List<ProjectDetails> projects = projectRepository.findByUploaderUserId(clientUserId);
        List<PaymentDetails> allPayments = paymentDetailsRepository.findByUserUserId(clientUserId);

        return projects.stream()
                .map(project -> buildProjectPaymentSummary(project, client, allPayments))
                .collect(Collectors.toList());
    }

    /**
     * Single project detail — with history + payments attached.
     */
    public ProjectPaymentSummaryDTO getMyClientProjectDetail(Long userId,
                                                             Long clientId,
                                                             Long projectId) {

        PartnerDetails partner = requireApprovedPartner(userId);
        assertClientBelongsToPartner(partner, clientId);

        ClientDetails client = clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getUploader().getUserId().equals(client.getUser().getUserId())) {
            throw new RuntimeException("This project does not belong to this client");
        }

        List<PaymentDetails> clientPayments = paymentDetailsRepository
                .findByUserUserId(client.getUser().getUserId());

        return buildProjectPaymentSummary(project, client, clientPayments);
    }

    /**
     * Project status history for a specific project.
     */
    public List<ProjectStatusDTO> getMyClientProjectHistory(Long userId,
                                                            Long clientId,
                                                            Long projectId) {

        PartnerDetails partner = requireApprovedPartner(userId);
        assertClientBelongsToPartner(partner, clientId);

        ClientDetails client = clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!project.getUploader().getUserId().equals(client.getUser().getUserId())) {
            throw new RuntimeException("This project does not belong to this client");
        }

        return projectStatusRepository.findByProjectProjectId(projectId)
                .stream()
                .map(this::toStatusDTO)
                .collect(Collectors.toList());
    }

    /**
     * All projects across all clients of this partner.
     */
    public List<ProjectPaymentSummaryDTO> getAllMyClientsProjects(Long userId) {

        PartnerDetails partner = requireApprovedPartner(userId);

        List<PartnerClientMapping> mappings =
                mappingRepository.findActiveWithClientByPartnerId(partner.getPartnerId());

        List<ProjectPaymentSummaryDTO> result = new ArrayList<>();

        for (PartnerClientMapping m : mappings) {
            ClientDetails client = m.getClient();
            Long clientUserId = client.getUser().getUserId();

            List<ProjectDetails> projects = projectRepository.findByUploaderUserId(clientUserId);
            List<PaymentDetails> clientPayments = paymentDetailsRepository.findByUserUserId(clientUserId);

            for (ProjectDetails project : projects) {
                result.add(buildProjectPaymentSummary(project, client, clientPayments));
            }
        }

        return result;
    }

    // =====================================================================
    // 8. PARTNER — create project for own client
    // =====================================================================

    @Transactional
    public PartnerProjectSummaryDTO createProjectForClient(Long userId,
                                                           Long clientId,
                                                           PartnerCreateProjectRequest req) {

        PartnerDetails partner = requireApprovedPartner(userId);
        assertClientBelongsToPartner(partner, clientId);

        if (isBlank(req.getProjectName()))
            throw new RuntimeException("Project name is required");

        ClientDetails client = clientDetailsRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        ProjectDetails project = new ProjectDetails();
        project.setProjectName(req.getProjectName());
        project.setProjectDescription(req.getProjectDescription());
        project.setProjectType(req.getProjectType());
        project.setCost(req.getCost());
        project.setDeadline(req.getDeadline());
        project.setUploader(client.getUser());
        project.setStatus("PENDING");

        if (req.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(req.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            project.setDepartment(dept);
        }

        project = projectRepository.save(project);
        log.info("[PARTNER-PROJECT] partnerId={} created projectId={} for clientId={}",
                partner.getPartnerId(), project.getProjectId(), clientId);

        sendProjectCreatedEmail(client, project, partner);

        return toProjectSummary(project);
    }

    // =====================================================================
    // PROJECT PAYMENT SUMMARY BUILDER (reused by 3 methods)
    // =====================================================================

    private ProjectPaymentSummaryDTO buildProjectPaymentSummary(ProjectDetails project,
                                                                ClientDetails client,
                                                                List<PaymentDetails> allClientPayments) {

        // Filter payments belonging to this project via ProjectPayment join
        List<PaymentDetails> projectPayments = new ArrayList<>();
        List<ProjectPayment> projectPaymentLinks =
                projectPaymentRepository.findByProjectProjectId(project.getProjectId());

        Set<Long> linkedPaymentIds = projectPaymentLinks.stream()
                .map(pp -> pp.getPayment().getPaymentId())
                .collect(Collectors.toSet());

        for (PaymentDetails p : allClientPayments) {
            if (linkedPaymentIds.contains(p.getPaymentId())) {
                projectPayments.add(p);
            }
        }

        // Amounts
        BigDecimal total = parseAmount(project.getCost());
        if (total == null) total = BigDecimal.ZERO;

        BigDecimal paid = BigDecimal.ZERO;
        for (PaymentDetails p : projectPayments) {
            if ("APPROVED".equalsIgnoreCase(p.getStatus())) {
                BigDecimal amt = parseAmount(p.getAmount());
                if (amt != null) paid = paid.add(amt);
            }
        }

        BigDecimal remaining = total.subtract(paid);
        if (remaining.signum() < 0) remaining = BigDecimal.ZERO;

        double paidPercentage = total.signum() == 0
                ? 0.0
                : paid.doubleValue() / total.doubleValue() * 100.0;

        boolean fullyPaid = remaining.signum() == 0 && total.signum() > 0;

        // Payment DTOs
        List<PaymentResponseDTO> paymentDTOs = projectPayments.stream()
                .sorted(Comparator.comparing(
                        PaymentDetails::getDateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toPaymentResponseDTO)
                .collect(Collectors.toList());

        return ProjectPaymentSummaryDTO.builder()
                .projectId(project.getProjectId())
                .projectName(project.getProjectName())
                .projectDescription(project.getProjectDescription())
                .projectType(project.getProjectType())
                .status(project.getStatus())
                .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
                .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
                .clientId(client.getId())
                .clientName(client.getUser().getName())
                .clientEmail(client.getUser().getEmail())
                .assignedManagerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
                .deadline(project.getDeadline() != null ? project.getDeadline().toString() : null)
                .totalProjectAmount(total.toPlainString())
                .paidAmount(paid.toPlainString())
                .remainingAmount(remaining.toPlainString())
                .paidPercentage(paidPercentage)
                .isFullyPaid(fullyPaid)
                .payments(paymentDTOs)
                .build();
    }

    // =====================================================================
    // EMAIL HELPERS (unchanged — same as before)
    // =====================================================================

    private void sendPartnerWelcomeEmail(PartnerDetails partner) {
        try {
            User u = partner.getUser();
            if (isBlank(u.getEmail())) return;

            String subject = "🤝 Partner Registration Received - Softmax Technologies";
            String html = String.format("""
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;">
                <div style="max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);">
                    <div style="background:linear-gradient(135deg,#667eea 0%%,#764ba2 100%%);padding:40px 30px;text-align:center;">
                        <h1 style="color:#fff;margin:0;font-size:26px;">🤝 Welcome to Partner Program</h1>
                        <p style="color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;">Softmax Technologies and IT Services</p>
                    </div>
                    <div style="padding:40px 35px;">
                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                        <p style="color:#4a5568;font-size:15px;">Thank you for registering as a partner. Your application for <strong>%s</strong> has been received successfully.</p>
                        <div style="background:#f0f4ff;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #667eea;">
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🔖 <strong>Partner Code:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🏢 <strong>Company:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📧 <strong>Email:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📊 <strong>Status:</strong> <span style="background:#fff9e6;color:#b8860b;padding:6px 16px;border-radius:20px;font-size:13px;font-weight:600;">⏳ PENDING APPROVAL</span></p>
                        </div>
                        <p style="color:#4a5568;">Our Head Director will review your agreement and approve your account shortly. You will be able to create clients once approved.</p>
                        <p style="color:#4a5568;font-size:13px;margin-top:24px;">Received on: %s</p>
                    </div>
                    <div style="background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;">
                        <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                        <p style="margin:6px 0;">🌐 <a href="https://softmax.in" style="color:#667eea;">softmax.in</a> | 💼 <a href="https://nexus.softmax.in" style="color:#667eea;">nexus.softmax.in</a></p>
                        <p style="margin-top:12px;">© 2026 Softmax Technologies - All Rights Reserved</p>
                    </div>
                </div></body></html>
                """,
                    safe(u.getName()),
                    safe(partner.getCompanyName()),
                    partner.getPartnerCode(),
                    safe(partner.getCompanyName()),
                    safe(u.getEmail()),
                    LocalDateTime.now().format(emailDateFmt)
            );
            emailService.sendEmail(u.getEmail(), subject, html);
            log.info("[PARTNER-EMAIL] Welcome email sent → {}", u.getEmail());
        } catch (Exception e) {
            log.warn("[PARTNER-EMAIL] Welcome email failed: {}", e.getMessage());
        }
    }

    private void sendPartnerStatusEmail(PartnerDetails partner, String status, String note) {
        try {
            User u = partner.getUser();
            if (isBlank(u.getEmail())) return;

            String icon, color, message;
            switch (status) {
                case "APPROVED":
                    icon = "✅"; color = "#11998e";
                    message = "Your partner account has been <strong>APPROVED</strong>. You can now create and manage clients.";
                    break;
                case "BLOCKED":
                    icon = "🚫"; color = "#dc2626";
                    message = "Your partner account has been <strong>BLOCKED</strong>. Please contact support for details.";
                    break;
                case "DEACTIVATED":
                    icon = "⏸️"; color = "#6b7280";
                    message = "Your partner account has been <strong>DEACTIVATED</strong>. Please contact support to reactivate.";
                    break;
                default:
                    icon = "⏳"; color = "#f59e0b";
                    message = "Your partner account status has been updated to <strong>" + status + "</strong>.";
            }

            String subject = icon + " Partner Account " + status + " - Softmax";
            String html = String.format("""
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;">
                <div style="max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);">
                    <div style="background:%s;padding:40px 30px;text-align:center;">
                        <h1 style="color:#fff;margin:0;font-size:26px;">%s Partner Account Update</h1>
                        <p style="color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;">Softmax Technologies</p>
                    </div>
                    <div style="padding:40px 35px;">
                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                        <p style="color:#4a5568;font-size:15px;">%s</p>
                        <div style="background:#f8f9fa;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid %s;">
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🔖 <strong>Partner Code:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🏢 <strong>Company:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📊 <strong>New Status:</strong> %s</p>
                            %s
                        </div>
                        <p style="color:#4a5568;">Login to <a href="https://nexus.softmax.in" style="color:#667eea;">nexus.softmax.in</a> for more details.</p>
                    </div>
                    <div style="background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;">
                        <p>© 2026 Softmax Technologies - All Rights Reserved</p>
                    </div>
                </div></body></html>
                """,
                    color, icon,
                    safe(u.getName()),
                    message,
                    color,
                    partner.getPartnerCode(),
                    safe(partner.getCompanyName()),
                    status,
                    note != null ? "<p style=\"margin:8px 0;color:#2d3748;font-size:14px;\">📝 <strong>Note:</strong> " + safe(note) + "</p>" : ""
            );
            emailService.sendEmail(u.getEmail(), subject, html);
            log.info("[PARTNER-EMAIL] Status email ({}) sent → {}", status, u.getEmail());
        } catch (Exception e) {
            log.warn("[PARTNER-EMAIL] Status email failed: {}", e.getMessage());
        }
    }

    private void sendClientWelcomeEmail(User clientUser, ClientDetails client, PartnerDetails partner) {
        try {
            if (clientUser == null || isBlank(clientUser.getEmail())) return;

            String subject = "🏢 Welcome to Softmax - Your Client Account is Ready";
            String html = String.format("""
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;">
                <div style="max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);">
                    <div style="background:linear-gradient(135deg,#2196F3 0%%,#1a237e 100%%);padding:40px 30px;text-align:center;">
                        <h1 style="color:#fff;margin:0;font-size:26px;">🏢 Welcome to Softmax</h1>
                        <p style="color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;">Your client account is ready</p>
                    </div>
                    <div style="padding:40px 35px;">
                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                        <p style="color:#4a5568;font-size:15px;">Your client account has been created for <strong>%s</strong> via our partner <strong>%s</strong>.</p>
                        <div style="background:#e3f2fd;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #2196F3;">
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🏢 <strong>Company:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📧 <strong>Email:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">🤝 <strong>Brought by Partner:</strong> %s</p>
                        </div>
                        <p style="color:#4a5568;"><strong>Next step:</strong> Sign up at <a href="https://nexus.softmax.in" style="color:#2196F3;">nexus.softmax.in</a> using your email <strong>%s</strong> to access your dashboard.</p>
                        <div style="text-align:center;margin-top:24px;">
                            <a href="https://nexus.softmax.in" style="display:inline-block;background:linear-gradient(135deg,#2196F3,#1a237e);color:#fff;padding:14px 34px;text-decoration:none;border-radius:50px;font-weight:600;">🚀 Go to Dashboard</a>
                        </div>
                    </div>
                    <div style="background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;">
                        <p>© 2026 Softmax Technologies - All Rights Reserved</p>
                    </div>
                </div></body></html>
                """,
                    safe(clientUser.getName()),
                    safe(client.getCompanyName()),
                    safe(partner.getCompanyName()),
                    safe(client.getCompanyName()),
                    safe(clientUser.getEmail()),
                    safe(partner.getCompanyName()),
                    safe(clientUser.getEmail())
            );
            emailService.sendEmail(clientUser.getEmail(), subject, html);
            log.info("[PARTNER-EMAIL] Client welcome email sent → {}", clientUser.getEmail());
        } catch (Exception e) {
            log.warn("[PARTNER-EMAIL] Client welcome email failed: {}", e.getMessage());
        }
    }

    private void sendProjectCreatedEmail(ClientDetails client, ProjectDetails project, PartnerDetails partner) {
        try {
            User clientUser = client.getUser();
            if (clientUser == null || isBlank(clientUser.getEmail())) return;

            String subject = "📁 New Project Created: " + safe(project.getProjectName()) + " - Softmax";
            String html = String.format("""
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head><body style="font-family:Arial,sans-serif;background:#f0f2f5;margin:0;padding:0;">
                <div style="max-width:600px;margin:30px auto;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 12px 45px rgba(0,0,0,0.12);">
                    <div style="background:linear-gradient(135deg,#11998e 0%%,#38ef7d 100%%);padding:40px 30px;text-align:center;">
                        <h1 style="color:#fff;margin:0;font-size:26px;">📁 New Project Created</h1>
                        <p style="color:rgba(255,255,255,0.95);margin:10px 0 0;font-size:14px;">Softmax Technologies</p>
                    </div>
                    <div style="padding:40px 35px;">
                        <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                        <p style="color:#4a5568;font-size:15px;">A new project has been created for your account by our partner <strong>%s</strong>.</p>
                        <div style="background:#e8f5e9;padding:22px;border-radius:12px;margin:22px 0;border-left:5px solid #11998e;">
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📁 <strong>Project:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📝 <strong>Type:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">💰 <strong>Cost:</strong> %s</p>
                            <p style="margin:8px 0;color:#2d3748;font-size:14px;">📊 <strong>Status:</strong> %s</p>
                        </div>
                        <p style="color:#4a5568;">Login to your dashboard for more details.</p>
                    </div>
                    <div style="background:#f8f9fa;padding:22px;text-align:center;border-top:1px solid #e0e0e0;font-size:12px;color:#888;">
                        <p>© 2026 Softmax Technologies - All Rights Reserved</p>
                    </div>
                </div></body></html>
                """,
                    safe(clientUser.getName()),
                    safe(partner.getCompanyName()),
                    safe(project.getProjectName()),
                    project.getProjectType() != null ? safe(project.getProjectType()) : "Not specified",
                    project.getCost() != null ? safe(project.getCost()) : "TBD",
                    safe(project.getStatus())
            );
            emailService.sendEmail(clientUser.getEmail(), subject, html);
            log.info("[PARTNER-EMAIL] Project created email sent → {}", clientUser.getEmail());
        } catch (Exception e) {
            log.warn("[PARTNER-EMAIL] Project email failed: {}", e.getMessage());
        }
    }

    // =====================================================================
    // GUARDS
    // =====================================================================

    private PartnerDetails requirePartner(Long userId) {
        return partnerRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RuntimeException("Access denied: Partner profile not found"));
    }

    private PartnerDetails requireApprovedPartner(Long userId) {
        PartnerDetails p = requirePartner(userId);
        if (!"APPROVED".equalsIgnoreCase(p.getPartnerStatus())
                || !Boolean.TRUE.equals(p.getIsApproved())) {
            throw new RuntimeException(
                    "Access denied: Partner account is not APPROVED (current: "
                            + p.getPartnerStatus() + "). Please wait for Head Director approval.");
        }
        return p;
    }

    private void assertClientBelongsToPartner(PartnerDetails partner, Long clientId) {
        boolean owned = mappingRepository.isClientOwnedByPartner(partner.getPartnerId(), clientId);
        if (!owned) {
            throw new RuntimeException("Access denied: This client does not belong to you");
        }
    }

    private boolean isHeadDirector(Long userId) {
        Optional<EmployeeDetails> emp = employeeDetailsRepository.findByUserUserId(userId);
        if (emp.isEmpty()) return false;
        boolean isDirector = userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
                .map(ut -> "DIRECTOR".equals(ut.getStype())).orElse(false);
        return isDirector && emp.get().getDepartmentRef() != null
                && HEAD_DEPARTMENT_ID.equals(emp.get().getDepartmentRef().getDepartmentId());
    }

    private void validateHeadDirector(Long userId) {
        if (!isHeadDirector(userId))
            throw new RuntimeException("Access denied: Only Head Director can perform this action");
    }

    // =====================================================================
    // MAPPERS & UTILS
    // =====================================================================

    private PartnerResponseDTO toPartnerDTO(PartnerDetails p) {
        long totalClients = mappingRepository.countByPartnerPartnerId(p.getPartnerId());
        long activeClients = mappingRepository
                .countByPartnerPartnerIdAndMappingStatus(p.getPartnerId(), "ACTIVE");

        return PartnerResponseDTO.builder()
                .partnerId(p.getPartnerId())
                .userId(p.getUser().getUserId())
                .partnerCode(p.getPartnerCode())
                .partnerName(p.getUser().getName())
                .partnerEmail(p.getUser().getEmail())
                .partnerPhotoUrl(p.getUser().getPhotoUrl())
                .companyName(p.getCompanyName())
                .companyEmail(p.getCompanyEmail())
                .companyMobileNumber(p.getCompanyMobileNumber())
                .companyWebsite(p.getCompanyWebsite())
                .companyAddress(p.getCompanyAddress())
                .contactPersonName(p.getContactPersonName())
                .contactPersonDesignation(p.getContactPersonDesignation())
                .servicesOffered(p.getServicesOffered())
                .agreementType(p.getAgreementType())
                .agreementValue(p.getAgreementValue())
                .agreementPdfDriveLink(p.getAgreementPdfDriveLink())
                .agreementStartDate(p.getAgreementStartDate())
                .agreementEndDate(p.getAgreementEndDate())
                .partnerStatus(p.getPartnerStatus())
                .isApproved(p.getIsApproved())
                .directorNote(p.getDirectorNote())
                .totalClients(totalClients)
                .activeClients(activeClients)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private PartnerClientResponseDTO toClientDTO(PartnerClientMapping m) {
        ClientDetails c = m.getClient();
        User u = c.getUser();
        return PartnerClientResponseDTO.builder()
                .mappingId(m.getMappingId())
                .clientId(c.getId())
                .userId(u.getUserId())
                .name(u.getName())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .companyName(c.getCompanyName())
                .companyEmail(c.getCompanyEmail())
                .clientStatus(c.getClientStatus())
                .isApproved(c.getIsApproved())
                .assignedAt(m.getAssignedAt())
                .mappingStatus(m.getMappingStatus())
                .notes(m.getNotes())
                .build();
    }

    private PartnerProjectSummaryDTO toProjectSummary(ProjectDetails p) {
        return PartnerProjectSummaryDTO.builder()
                .projectId(p.getProjectId())
                .projectName(p.getProjectName())
                .projectDescription(p.getProjectDescription())
                .projectType(p.getProjectType())
                .status(p.getStatus())
                .cost(p.getCost())
                .deadline(p.getDeadline())
                .departmentId(p.getDepartment() != null ? p.getDepartment().getDepartmentId() : null)
                .departmentName(p.getDepartment() != null ? p.getDepartment().getDepartmentName() : null)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    /**
     * PaymentDetails → PaymentResponseDTO
     * Agar PaymentResponseDTO ke fields match nahi karte to ye method
     * compile error dega — us waqt PaymentResponseDTO ka code paste karna.
     */
    private PaymentResponseDTO toPaymentResponseDTO(PaymentDetails p) {
        if (p == null) return null;
        return PaymentResponseDTO.builder()
                .paymentId(p.getPaymentId())
                .transactionId(p.getTransactionId())
                .dateTime(p.getDateTime())
                .type(p.getType())
                .amount(p.getAmount())
                .status(p.getStatus())
                .paymentNote(p.getPaymentNote())
                .userName(p.getUser() != null ? p.getUser().getName() : null)
                .userEmail(p.getUser() != null ? p.getUser().getEmail() : null)
                .approvedByName(p.getApprovedBy() != null ? p.getApprovedBy().getName() : null)
                .approvedAt(p.getApprovedAt())
                .directorNote(p.getDirectorNote())
                .build();
    }

    /**
     * ProjectStatus → ProjectStatusDTO
     */
    private ProjectStatusDTO toStatusDTO(ProjectStatus status) {
        return ProjectStatusDTO.builder()
                .statusId(status.getStatusId())
                .projectId(status.getProject().getProjectId())
                .projectName(status.getProject().getProjectName())
                .status(status.getStatus())
                .dateTime(status.getDateTime())
                .handledById(status.getHandledBy() != null ? status.getHandledBy().getUserId() : null)
                .handledByName(status.getHandledBy() != null ? status.getHandledBy().getName() : null)
                .assignedToId(status.getAssignedTo() != null ? status.getAssignedTo().getUserId() : null)
                .assignedToName(status.getAssignedTo() != null ? status.getAssignedTo().getName() : null)
                .directorNote(status.getDirectorNote())
                .managerNote(status.getManagerNote())
                .deadline(status.getDeadline())
                .build();
    }

    private String generatePartnerCode() {
        String code;
        do {
            code = "PTR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (partnerRepository.existsByPartnerCode(code));
        return code;
    }

    private BigDecimal parseAmount(String amt) {
        if (amt == null || amt.isBlank()) return null;
        try { return new BigDecimal(amt.trim()); } catch (Exception e) { return null; }
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}