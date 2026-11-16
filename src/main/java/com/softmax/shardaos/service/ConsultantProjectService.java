package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.output.*;
import com.softmax.shardaos.dto.input.ConsultantRatingRequestDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConsultantProjectService {

    @Autowired
    private ProjectDetailsRepository projectRepository;

    @Autowired
    private ProjectRequestRepository projectRequestRepository;

    @Autowired
    private ConsultantDetailsRepository consultantDetailsRepository;

    @Autowired
    private ConsultantReviewRepository consultantReviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    @Autowired
    private TeamMembersRepository teamMembersRepository;

    @Autowired
    private EmailService emailService;

    // ==================== CONSULTANT APIs ====================

    public List<ConsultantProjectResponseDTO> getDepartmentProjects(Long consultantId) {
        ConsultantDetails consultant = consultantDetailsRepository.findByUserUserId(consultantId)
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Consultant not found"));

        if (consultant.getDepartment() == null) {
            throw new RuntimeException("Consultant not assigned to any department");
        }

        Long departmentId = consultant.getDepartment().getDepartmentId();
        List<ProjectDetails> projects = projectRepository.findByDepartmentDepartmentId(departmentId);

        return projects.stream().map(project -> {
            boolean hasRequested = projectRequestRepository.existsByProjectProjectIdAndUserUserId(
                    project.getProjectId(), consultantId);

            return ConsultantProjectResponseDTO.builder()
                    .projectId(project.getProjectId())
                    .projectName(project.getProjectName())
                    .projectDescription(project.getProjectDescription())
                    .projectType(project.getProjectType())
                    .status(project.getStatus())
                    .departmentId(departmentId)
                    .departmentName(consultant.getDepartment().getDepartmentName())
                    .clientId(project.getUploader().getUserId())
                    .clientName(project.getUploader().getName())
                    .managerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
                    .managerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
                    .hasRequested(hasRequested)
                    .isAssigned(false)
                    .build();
        }).collect(Collectors.toList());
    }

    public List<ConsultantProjectResponseDTO> getMyAssignedProjects(Long consultantId) {
        if (!isConsultant(consultantId)) {
            throw new RuntimeException("Only consultants can view their assigned projects");
        }

        List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(consultantId);

        Set<Long> projectIds = userTeams.stream()
                .map(tm -> tm.getTeam().getProject().getProjectId())
                .collect(Collectors.toSet());

        if (projectIds.isEmpty()) {
            return List.of();
        }

        List<ProjectDetails> assignedProjects = projectRepository.findAllById(projectIds);

        return assignedProjects.stream().map(project -> {
            boolean hasRequested = projectRequestRepository.existsByProjectProjectIdAndUserUserId(
                    project.getProjectId(), consultantId);

            return ConsultantProjectResponseDTO.builder()
                    .projectId(project.getProjectId())
                    .projectName(project.getProjectName())
                    .projectDescription(project.getProjectDescription())
                    .projectType(project.getProjectType())
                    .status(project.getStatus())
                    .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
                    .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
                    .clientId(project.getUploader().getUserId())
                    .clientName(project.getUploader().getName())
                    .managerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
                    .managerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
                    .hasRequested(hasRequested)
                    .isAssigned(true)
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public String requestToJoinProject(Long consultantId, Long projectId) {
        ConsultantDetails consultant = consultantDetailsRepository.findByUserUserId(consultantId)
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Consultant not found"));

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (consultant.getDepartment() == null ||
                !consultant.getDepartment().getDepartmentId().equals(project.getDepartment().getDepartmentId())) {
            throw new RuntimeException("You can only request projects in your department");
        }

        if (projectRequestRepository.existsByProjectProjectIdAndUserUserId(projectId, consultantId)) {
            throw new RuntimeException("You have already requested this project");
        }

        ProjectRequest request = new ProjectRequest();
        request.setProject(project);
        request.setUser(userRepository.findById(consultantId).get());

        projectRequestRepository.save(request);

        if (project.getAssignedManager() != null) {
            sendRequestEmail(consultant, project);
        }

        return "Request sent successfully";
    }

    public List<ConsultantRequestResponseDTO> getMyRequests(Long consultantId) {
        List<ProjectRequest> requests = projectRequestRepository.findByUserUserId(consultantId);

        return requests.stream().map(req -> {
            ConsultantDetails consultant = consultantDetailsRepository.findByUserUserId(consultantId)
                    .stream().findFirst().orElse(null);

            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(consultantId);
            Integer reviewCount = consultantReviewRepository.getReviewCountByUserId(consultantId);

            return ConsultantRequestResponseDTO.builder()
                    .requestId(req.getRequestId())
                    .projectId(req.getProject().getProjectId())
                    .projectName(req.getProject().getProjectName())
                    .consultantId(consultantId)
                    .consultantName(req.getUser().getName())
                    .consultantEmail(req.getUser().getEmail())
                    .consultantPhoto(req.getUser().getPhotoUrl())
                    .skill(consultant != null ? consultant.getSkill() : null)
                    .experience(consultant != null ? consultant.getExperience() : null)
                    .marks10(consultant != null ? consultant.getMarks10() : null)
                    .marks12(consultant != null ? consultant.getMarks12() : null)
                    .graduationMarks(consultant != null ? consultant.getGraduationMarks() : null)
                    .resumeLink(consultant != null ? consultant.getResumeLink() : null)
                    .averageRating(avgRating != null ? avgRating : 0.0)
                    .totalReviews(reviewCount != null ? reviewCount : 0)
                    .requestedAt(req.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    public Boolean hasRequested(Long consultantId, Long projectId) {
        return projectRequestRepository.existsByProjectProjectIdAndUserUserId(projectId, consultantId);
    }

    public List<ConsultantRatingResponseDTO> getConsultantRatings(Long consultantUserId) {
        if (!isConsultant(consultantUserId)) {
            throw new RuntimeException("Only consultants can view their ratings");
        }

        List<ConsultantReview> reviews = consultantReviewRepository.findByUserUserId(consultantUserId);

        return reviews.stream().map(review ->
                ConsultantRatingResponseDTO.builder()
                        .reviewId(review.getReviewId())
                        .consultantId(consultantUserId)
                        .consultantName(review.getUser().getName())
                        .reviewerId(0L)
                        .reviewerName("Manager")
                        .reviewMarks(review.getReviewMarks())
                        .reviewNote(review.getReviewNote())
                        .createdAt(review.getCreatedAt())
                        .build()
        ).collect(Collectors.toList());
    }

    public Double getConsultantAverageRating(Long consultantUserId) {
        if (!isConsultant(consultantUserId)) {
            throw new RuntimeException("Only consultants can view their ratings");
        }
        Double avgRating = consultantReviewRepository.getAverageRatingByUserId(consultantUserId);
        return avgRating != null ? avgRating : 0.0;
    }

    // ==================== MANAGER APIs ====================

    public List<ConsultantRequestResponseDTO> getProjectRequestsForManager(Long managerId) {
        if (!isManager(managerId)) {
            throw new RuntimeException("Only managers can view requests");
        }

        List<ProjectRequest> requests = projectRequestRepository.findByProjectAssignedManagerUserId(managerId);

        return requests.stream().map(req -> {
            Long consultantId = req.getUser().getUserId();
            ConsultantDetails consultant = consultantDetailsRepository.findByUserUserId(consultantId)
                    .stream().findFirst().orElse(null);

            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(consultantId);
            Integer reviewCount = consultantReviewRepository.getReviewCountByUserId(consultantId);

            return ConsultantRequestResponseDTO.builder()
                    .requestId(req.getRequestId())
                    .projectId(req.getProject().getProjectId())
                    .projectName(req.getProject().getProjectName())
                    .consultantId(consultantId)
                    .consultantName(req.getUser().getName())
                    .consultantEmail(req.getUser().getEmail())
                    .consultantPhoto(req.getUser().getPhotoUrl())
                    .skill(consultant != null ? consultant.getSkill() : null)
                    .experience(consultant != null ? consultant.getExperience() : null)
                    .marks10(consultant != null ? consultant.getMarks10() : null)
                    .marks12(consultant != null ? consultant.getMarks12() : null)
                    .graduationMarks(consultant != null ? consultant.getGraduationMarks() : null)
                    .resumeLink(consultant != null ? consultant.getResumeLink() : null)
                    .averageRating(avgRating != null ? avgRating : 0.0)
                    .totalReviews(reviewCount != null ? reviewCount : 0)
                    .requestedAt(req.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public ConsultantRatingResponseDTO rateConsultant(Long managerId, ConsultantRatingRequestDTO request) {

        if (request.getRating() < 1.0 || request.getRating() > 5.0) {
            throw new RuntimeException("Rating must be between 1.0 and 5.0");
        }

        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        ConsultantDetails consultantDetails = consultantDetailsRepository.findById(request.getConsultantId())
                .orElseThrow(() -> new RuntimeException("Consultant not found"));

        User consultant = consultantDetails.getUser();

        ConsultantReview review = new ConsultantReview();
        review.setUser(consultant);
        review.setReviewMarks(request.getRating());
        review.setReviewNote(request.getFeedback());

        ConsultantReview savedReview = consultantReviewRepository.save(review);

        // Beautiful Email Logic
        try {
            String stars = request.getRating() >= 4.5 ? "★★★★★" :
                    request.getRating() >= 3.5 ? "★★★★☆" :
                            request.getRating() >= 2.5 ? "★★★☆☆" :
                                    request.getRating() >= 1.5 ? "★★☆☆☆" : "★☆☆☆☆";

            String ratingColor = request.getRating() >= 4.0 ? "#28a745" :
                    request.getRating() >= 3.0 ? "#ffc107" : "#dc3545";

            String html = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "<style>\n" +
                    "body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }\n" +
                    ".container { max-width: 550px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }\n" +
                    ".header { background: linear-gradient(135deg, #667eea, #764ba2); padding: 35px 25px; text-align: center; }\n" +
                    ".header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }\n" +
                    ".content { padding: 35px 30px; }\n" +
                    ".rating-box { background: #f8f9fa; border-radius: 14px; padding: 25px; text-align: center; margin: 22px 0; border: 1px solid #e9ecef; }\n" +
                    ".stars { font-size: 34px; letter-spacing: 4px; color: #ffc107; }\n" +
                    ".rating-value { font-size: 46px; font-weight: bold; color: " + ratingColor + "; margin-top: 8px; }\n" +
                    ".feedback-box { background: #e8f4fd; border-radius: 12px; padding: 18px 22px; margin-top: 18px; border-left: 5px solid #667eea; }\n" +
                    ".feedback-box p { margin: 6px 0; color: #2d3748; font-size: 14px; }\n" +
                    ".btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); color: white !important; padding: 13px 32px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 20px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }\n" +
                    ".footer { background: #f8f9fa; padding: 22px; text-align: center; font-size: 12px; color: #888; border-top: 1px solid #e0e0e0; }\n" +
                    ".footer a { color: #667eea; text-decoration: none; font-weight: 600; }\n" +
                    "</style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<div class='container'>\n" +
                    "<div class='header'>\n" +
                    "<h1>⭐ New Rating Received!</h1>\n" +
                    "</div>\n" +
                    "<div class='content'>\n" +
                    "<h3 style='color:#333;margin-top:0;'>Hello <strong>" + consultant.getName() + "</strong>,</h3>\n" +
                    "<p style='color:#4a5568;'><strong>" + manager.getName() + "</strong> has rated your performance.</p>\n" +
                    "<div class='rating-box'>\n" +
                    "<div class='stars'>" + stars + "</div>\n" +
                    "<div class='rating-value'>" + request.getRating() + " / 5.0</div>\n" +
                    "</div>\n" +
                    "<div class='feedback-box'>\n" +
                    "<p><strong>📝 Feedback:</strong></p>\n" +
                    "<p>\"" + (request.getFeedback() != null ? request.getFeedback() : "No additional comments") + "\"</p>\n" +
                    "</div>\n" +
                    "<div style='text-align:center;'>\n" +
                    "<a href='https://nexus.softmax.in' class='btn'>🚀 View All My Ratings</a>\n" +
                    "</div>\n" +
                    "</div>\n" +
                    "<div class='footer'>\n" +
                    "<p style='margin:6px 0;'><strong>Softmax Technologies and IT Services</strong></p>\n" +
                    "<p style='margin:6px 0;'>🌐 <a href='https://softmax.in'>softmax.in</a> | 💼 <a href='https://nexus.softmax.in'>nexus.softmax.in</a></p>\n" +
                    "<p style='margin:6px 0;'>📧 <a href='mailto:softmaxdigitals@gmail.com'>softmaxdigitals@gmail.com</a></p>\n" +
                    "<p style='margin-top:12px;'>© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>\n" +
                    "<p>Keep up the great work! 💪</p>\n" +
                    "</div>\n" +
                    "</div>\n" +
                    "</body>\n" +
                    "</html>";

            emailService.sendEmail(consultant.getEmail(), "⭐ You've Received a New Rating! - Softmax Technologies and IT Services", html);
            System.out.println("✅ Email sent to: " + consultant.getEmail());

        } catch (Exception e) {
            System.out.println("❌ Email error: " + e.getMessage());
        }

        return ConsultantRatingResponseDTO.builder()
                .reviewId(savedReview.getReviewId())
                .consultantId(request.getConsultantId())
                .consultantName(consultant.getName())
                .reviewerId(managerId)
                .reviewerName(manager.getName())
                .reviewMarks(request.getRating())
                .reviewNote(request.getFeedback())
                .createdAt(savedReview.getCreatedAt())
                .build();
    }


    public List<ConsultantWithRatingDTO> getAllConsultantsWithRatings(Long managerId, Long departmentId) {
        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);

        return consultants.stream().map(c -> {
            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(c.getUser().getUserId());
            Integer reviewCount = consultantReviewRepository.getReviewCountByUserId(c.getUser().getUserId());

            return ConsultantWithRatingDTO.builder()
                    .consultantId(c.getConsultantId())
                    .userId(c.getUser().getUserId())
                    .name(c.getUser().getName())
                    .email(c.getUser().getEmail())
                    .photoUrl(c.getUser().getPhotoUrl())
                    .skill(c.getSkill())
                    .experience(c.getExperience())
                    .averageRating(avgRating != null ? avgRating : 0.0)
                    .totalReviews(reviewCount != null ? reviewCount : 0)
                    .build();
        }).collect(Collectors.toList());
    }

    public List<ConsultantWithRatingDTO> getMyDepartmentConsultants(Long managerId) {
        if (!isManager(managerId)) {
            throw new RuntimeException("Only managers can view department consultants");
        }

        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(managerId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (employee.getDepartmentRef() == null) {
            throw new RuntimeException("Manager not assigned to any department");
        }

        Long departmentId = employee.getDepartmentRef().getDepartmentId();

        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);

        return consultants.stream().map(c -> {
            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(c.getUser().getUserId());
            Integer reviewCount = consultantReviewRepository.getReviewCountByUserId(c.getUser().getUserId());

            return ConsultantWithRatingDTO.builder()
                    .consultantId(c.getConsultantId())
                    .userId(c.getUser().getUserId())
                    .name(c.getUser().getName())
                    .email(c.getUser().getEmail())
                    .photoUrl(c.getUser().getPhotoUrl())
                    .skill(c.getSkill())
                    .experience(c.getExperience())
                    .averageRating(avgRating != null ? avgRating : 0.0)
                    .totalReviews(reviewCount != null ? reviewCount : 0)
                    .build();
        }).collect(Collectors.toList());
    }

    // ==================== HELPER METHODS ====================

    private boolean isManager(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
                .filter(ut -> "MANAGER".equals(ut.getStype()))
                .isPresent();
    }

    private boolean isConsultant(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "CONSULTANT").isPresent();
    }

    // ==================== EMAIL METHODS ====================

    private void sendRequestEmail(ConsultantDetails consultant, ProjectDetails project) {
        Double avgRating = consultantReviewRepository.getAverageRatingByUserId(consultant.getUser().getUserId());
        String subject = "📋 New Consultant Request: " + project.getProjectName() + " - Softmax Technologies and IT Services";
        String html = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                    .header { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); padding: 40px 30px; text-align: center; }
                    .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                    .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                    .content { padding: 40px 35px; }
                    .info-box { background: #e8f5e9; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #11998e; }
                    .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                    .btn { display: inline-block; background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
                    .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                    .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>📋 New Consultant Request</h1>
                        <p>Softmax Technologies and IT Services</p>
                    </div>
                    <div class="content">
                        <p style="color:#2d3748;font-size:15px;">A consultant has requested to join your project.</p>
                        <div class="info-box">
                            <p>👤 <strong>Consultant:</strong> %s</p>
                            <p>📁 <strong>Project:</strong> %s</p>
                            <p>🏢 <strong>Department:</strong> %s</p>
                            <p>🛠️ <strong>Skills:</strong> %s</p>
                            <p>💼 <strong>Experience:</strong> %s</p>
                            <p>⭐ <strong>Rating:</strong> %.1f/5.0</p>
                        </div>
                        <p style="color:#4a5568;">Please login to view details and assign.</p>
                        <div style="text-align: center;">
                            <a href="https://nexus.softmax.in" class="btn">🚀 View Request</a>
                        </div>
                    </div>
                    <div class="footer">
                        <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                        <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                        <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                        <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                consultant.getUser().getName(),
                project.getProjectName(),
                consultant.getDepartment().getDepartmentName(),
                consultant.getSkill(),
                consultant.getExperience(),
                avgRating != null ? avgRating : 0.0
        );
        emailService.sendEmail(project.getAssignedManager().getEmail(), subject, html);
    }

    private void sendRatingEmailToConsultant(User consultant, User manager, Double rating, String feedback) {
        String subject = "⭐ You've Received a New Rating! - Softmax Technologies and IT Services";
        String stars = rating >= 4.5 ? "★★★★★" : rating >= 3.5 ? "★★★★☆" : rating >= 2.5 ? "★★★☆☆" : rating >= 1.5 ? "★★☆☆☆" : "★☆☆☆☆";
        String ratingColor = rating >= 4.0 ? "#28a745" : rating >= 3.0 ? "#ffc107" : "#dc3545";

        String html = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                    .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center; }
                    .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                    .content { padding: 40px 35px; }
                    .rating-box { background: #f8f9fa; border-radius: 14px; padding: 25px; text-align: center; margin: 22px 0; border: 1px solid #e9ecef; }
                    .stars { font-size: 34px; letter-spacing: 4px; color: #ffc107; }
                    .rating-value { font-size: 46px; font-weight: bold; color: %s; margin-top: 8px; }
                    .feedback-box { background: #e8f4fd; border-radius: 12px; padding: 18px 22px; margin-top: 18px; border-left: 5px solid #667eea; }
                    .feedback-box p { margin: 6px 0; color: #2d3748; font-size: 14px; }
                    .btn { display: inline-block; background: linear-gradient(135deg,#667eea 0%,#764ba2 100%); color: white !important; padding: 13px 32px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 20px; font-size: 15px; box-shadow: 0 6px 18px rgba(102,126,234,0.35); }
                    .footer { background: #f8f9fa; padding: 22px; text-align: center; font-size: 12px; color: #888; border-top: 1px solid #e0e0e0; }
                    .footer a { color: #667eea; text-decoration: none; font-weight: 600; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>⭐ New Rating Received!</h1>
                    </div>
                    <div class="content">
                        <h3 style="color:#333;margin-top:0;">Hello <strong>%s</strong>,</h3>
                        <p style="color:#4a5568;"><strong>%s</strong> has rated your performance.</p>
                        <div class="rating-box">
                            <div class="stars">%s</div>
                            <div class="rating-value">%.1f / 5.0</div>
                        </div>
                        <div class="feedback-box">
                            <p><strong>📝 Feedback:</strong></p>
                            <p>"%s"</p>
                        </div>
                        <div style="text-align: center;">
                            <a href="https://nexus.softmax.in" class="btn">🚀 View All My Ratings</a>
                        </div>
                    </div>
                    <div class="footer">
                        <p style="margin:6px 0;"><strong>Softmax Technologies and IT Services</strong></p>
                        <p style="margin:6px 0;">🌐 <a href="https://softmax.in">softmax.in</a> | 💼 <a href="https://nexus.softmax.in">nexus.softmax.in</a></p>
                        <p style="margin:6px 0;">📧 <a href="mailto:softmaxdigitals@gmail.com">softmaxdigitals@gmail.com</a></p>
                        <p style="margin-top:12px;">© 2026 Softmax Technologies and IT Services - All Rights Reserved</p>
                        <p>Keep up the great work! 💪</p>
                    </div>
                </div>
            </body>
            </html>
            """,
                ratingColor,
                consultant.getName(),
                manager.getName(),
                stars,
                rating,
                feedback != null ? feedback : "No additional comments"
        );

        emailService.sendEmail(consultant.getEmail(), subject, html);
    }
}










//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.output.*;
//import com.softmax.shardaos.dto.input.ConsultantRatingRequestDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//@Service
//public class ConsultantProjectService {
//
//    @Autowired
//    private ProjectDetailsRepository projectRepository;
//
//    @Autowired
//    private ProjectRequestRepository projectRequestRepository;
//
//    @Autowired
//    private ConsultantDetailsRepository consultantDetailsRepository;
//
//    @Autowired
//    private ConsultantReviewRepository consultantReviewRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private UserTypeRepository userTypeRepository;
//
//    @Autowired
//    private EmployeeDetailsRepository employeeDetailsRepository;
//
//    @Autowired
//    private TeamMembersRepository teamMembersRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    // ==================== CONSULTANT APIs ====================
//
//    public List<ConsultantProjectResponseDTO> getDepartmentProjects(Long consultantId) {
//        ConsultantDetails consultant = consultantDetailsRepository.findByUserUserId(consultantId)
//                .stream().findFirst()
//                .orElseThrow(() -> new RuntimeException("Consultant not found"));
//
//        if (consultant.getDepartment() == null) {
//            throw new RuntimeException("Consultant not assigned to any department");
//        }
//
//        Long departmentId = consultant.getDepartment().getDepartmentId();
//        List<ProjectDetails> projects = projectRepository.findByDepartmentDepartmentId(departmentId);
//
//        return projects.stream().map(project -> {
//            boolean hasRequested = projectRequestRepository.existsByProjectProjectIdAndUserUserId(
//                    project.getProjectId(), consultantId);
//
//            return ConsultantProjectResponseDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName())
//                    .projectDescription(project.getProjectDescription())
//                    .projectType(project.getProjectType())
//                    .status(project.getStatus())
//                    .departmentId(departmentId)
//                    .departmentName(consultant.getDepartment().getDepartmentName())
//                    .clientId(project.getUploader().getUserId())
//                    .clientName(project.getUploader().getName())
//                    .managerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
//                    .managerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
//                    .hasRequested(hasRequested)
//                    .isAssigned(false)
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
//    public List<ConsultantProjectResponseDTO> getMyAssignedProjects(Long consultantId) {
//        if (!isConsultant(consultantId)) {
//            throw new RuntimeException("Only consultants can view their assigned projects");
//        }
//
//        List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(consultantId);
//
//        Set<Long> projectIds = userTeams.stream()
//                .map(tm -> tm.getTeam().getProject().getProjectId())
//                .collect(Collectors.toSet());
//
//        if (projectIds.isEmpty()) {
//            return List.of();
//        }
//
//        List<ProjectDetails> assignedProjects = projectRepository.findAllById(projectIds);
//
//        return assignedProjects.stream().map(project -> {
//            boolean hasRequested = projectRequestRepository.existsByProjectProjectIdAndUserUserId(
//                    project.getProjectId(), consultantId);
//
//            return ConsultantProjectResponseDTO.builder()
//                    .projectId(project.getProjectId())
//                    .projectName(project.getProjectName())
//                    .projectDescription(project.getProjectDescription())
//                    .projectType(project.getProjectType())
//                    .status(project.getStatus())
//                    .departmentId(project.getDepartment() != null ? project.getDepartment().getDepartmentId() : null)
//                    .departmentName(project.getDepartment() != null ? project.getDepartment().getDepartmentName() : null)
//                    .clientId(project.getUploader().getUserId())
//                    .clientName(project.getUploader().getName())
//                    .managerId(project.getAssignedManager() != null ? project.getAssignedManager().getUserId() : null)
//                    .managerName(project.getAssignedManager() != null ? project.getAssignedManager().getName() : null)
//                    .hasRequested(hasRequested)
//                    .isAssigned(true)
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
//    @Transactional
//    public String requestToJoinProject(Long consultantId, Long projectId) {
//        ConsultantDetails consultant = consultantDetailsRepository.findByUserUserId(consultantId)
//                .stream().findFirst()
//                .orElseThrow(() -> new RuntimeException("Consultant not found"));
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        if (consultant.getDepartment() == null ||
//                !consultant.getDepartment().getDepartmentId().equals(project.getDepartment().getDepartmentId())) {
//            throw new RuntimeException("You can only request projects in your department");
//        }
//
//        if (projectRequestRepository.existsByProjectProjectIdAndUserUserId(projectId, consultantId)) {
//            throw new RuntimeException("You have already requested this project");
//        }
//
//        ProjectRequest request = new ProjectRequest();
//        request.setProject(project);
//        request.setUser(userRepository.findById(consultantId).get());
//
//        projectRequestRepository.save(request);
//
//        if (project.getAssignedManager() != null) {
//            sendRequestEmail(consultant, project);
//        }
//
//        return "Request sent successfully";
//    }
//
//    public List<ConsultantRequestResponseDTO> getMyRequests(Long consultantId) {
//        List<ProjectRequest> requests = projectRequestRepository.findByUserUserId(consultantId);
//
//        return requests.stream().map(req -> {
//            ConsultantDetails consultant = consultantDetailsRepository.findByUserUserId(consultantId)
//                    .stream().findFirst().orElse(null);
//
//            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(consultantId);
//            Integer reviewCount = consultantReviewRepository.getReviewCountByUserId(consultantId);
//
//            return ConsultantRequestResponseDTO.builder()
//                    .requestId(req.getRequestId())
//                    .projectId(req.getProject().getProjectId())
//                    .projectName(req.getProject().getProjectName())
//                    .consultantId(consultantId)
//                    .consultantName(req.getUser().getName())
//                    .consultantEmail(req.getUser().getEmail())
//                    .consultantPhoto(req.getUser().getPhotoUrl())
//                    .skill(consultant != null ? consultant.getSkill() : null)
//                    .experience(consultant != null ? consultant.getExperience() : null)
//                    .marks10(consultant != null ? consultant.getMarks10() : null)
//                    .marks12(consultant != null ? consultant.getMarks12() : null)
//                    .graduationMarks(consultant != null ? consultant.getGraduationMarks() : null)
//                    .resumeLink(consultant != null ? consultant.getResumeLink() : null)
//                    .averageRating(avgRating != null ? avgRating : 0.0)
//                    .totalReviews(reviewCount != null ? reviewCount : 0)
//                    .requestedAt(req.getCreatedAt())
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
//    public Boolean hasRequested(Long consultantId, Long projectId) {
//        return projectRequestRepository.existsByProjectProjectIdAndUserUserId(projectId, consultantId);
//    }
//
//    public List<ConsultantRatingResponseDTO> getConsultantRatings(Long consultantUserId) {
//        if (!isConsultant(consultantUserId)) {
//            throw new RuntimeException("Only consultants can view their ratings");
//        }
//
//        List<ConsultantReview> reviews = consultantReviewRepository.findByUserUserId(consultantUserId);
//
//        return reviews.stream().map(review ->
//                ConsultantRatingResponseDTO.builder()
//                        .reviewId(review.getReviewId())
//                        .consultantId(consultantUserId)
//                        .consultantName(review.getUser().getName())
//                        .reviewerId(0L)
//                        .reviewerName("Manager")
//                        .reviewMarks(review.getReviewMarks())
//                        .reviewNote(review.getReviewNote())
//                        .createdAt(review.getCreatedAt())
//                        .build()
//        ).collect(Collectors.toList());
//    }
//
//    public Double getConsultantAverageRating(Long consultantUserId) {
//        if (!isConsultant(consultantUserId)) {
//            throw new RuntimeException("Only consultants can view their ratings");
//        }
//        Double avgRating = consultantReviewRepository.getAverageRatingByUserId(consultantUserId);
//        return avgRating != null ? avgRating : 0.0;
//    }
//
//    // ==================== MANAGER APIs ====================
//
//    public List<ConsultantRequestResponseDTO> getProjectRequestsForManager(Long managerId) {
//        if (!isManager(managerId)) {
//            throw new RuntimeException("Only managers can view requests");
//        }
//
//        List<ProjectRequest> requests = projectRequestRepository.findByProjectAssignedManagerUserId(managerId);
//
//        return requests.stream().map(req -> {
//            Long consultantId = req.getUser().getUserId();
//            ConsultantDetails consultant = consultantDetailsRepository.findByUserUserId(consultantId)
//                    .stream().findFirst().orElse(null);
//
//            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(consultantId);
//            Integer reviewCount = consultantReviewRepository.getReviewCountByUserId(consultantId);
//
//            return ConsultantRequestResponseDTO.builder()
//                    .requestId(req.getRequestId())
//                    .projectId(req.getProject().getProjectId())
//                    .projectName(req.getProject().getProjectName())
//                    .consultantId(consultantId)
//                    .consultantName(req.getUser().getName())
//                    .consultantEmail(req.getUser().getEmail())
//                    .consultantPhoto(req.getUser().getPhotoUrl())
//                    .skill(consultant != null ? consultant.getSkill() : null)
//                    .experience(consultant != null ? consultant.getExperience() : null)
//                    .marks10(consultant != null ? consultant.getMarks10() : null)
//                    .marks12(consultant != null ? consultant.getMarks12() : null)
//                    .graduationMarks(consultant != null ? consultant.getGraduationMarks() : null)
//                    .resumeLink(consultant != null ? consultant.getResumeLink() : null)
//                    .averageRating(avgRating != null ? avgRating : 0.0)
//                    .totalReviews(reviewCount != null ? reviewCount : 0)
//                    .requestedAt(req.getCreatedAt())
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
////    @Transactional
////    public ConsultantRatingResponseDTO rateConsultant(Long managerId, ConsultantRatingRequestDTO request) {
////        if (request.getRating() < 1.0 || request.getRating() > 5.0) {
////            throw new RuntimeException("Rating must be between 1.0 and 5.0");
////        }
////
////        User manager = userRepository.findById(managerId)
////                .orElseThrow(() -> new RuntimeException("Manager not found"));
////
////        User consultant = userRepository.findById(request.getConsultantId())
////                .orElseThrow(() -> new RuntimeException("Consultant not found"));
////
////        consultantDetailsRepository.findByUserUserId(request.getConsultantId())
////                .stream().findFirst()
////                .orElseThrow(() -> new RuntimeException("Consultant details not found"));
////
////        ConsultantReview review = new ConsultantReview();
////        review.setUser(consultant);
////        review.setReviewMarks(request.getRating());
////        review.setReviewNote(request.getFeedback());
////
////        ConsultantReview savedReview = consultantReviewRepository.save(review);
////
////        sendRatingEmailToConsultant(consultant, manager, request.getRating(), request.getFeedback());
////
////        return ConsultantRatingResponseDTO.builder()
////                .reviewId(savedReview.getReviewId())
////                .consultantId(request.getConsultantId())
////                .consultantName(consultant.getName())
////                .reviewerId(managerId)
////                .reviewerName(manager.getName())
////                .reviewMarks(request.getRating())
////                .reviewNote(request.getFeedback())
////                .createdAt(savedReview.getCreatedAt())
////                .build();
////    }
//@Transactional
//public ConsultantRatingResponseDTO rateConsultant(Long managerId, ConsultantRatingRequestDTO request) {
//
//    if (request.getRating() < 1.0 || request.getRating() > 5.0) {
//        throw new RuntimeException("Rating must be between 1.0 and 5.0");
//    }
//
//    User manager = userRepository.findById(managerId)
//            .orElseThrow(() -> new RuntimeException("Manager not found"));
//
//    ConsultantDetails consultantDetails = consultantDetailsRepository.findById(request.getConsultantId())
//            .orElseThrow(() -> new RuntimeException("Consultant not found"));
//
//    User consultant = consultantDetails.getUser();
//
//    ConsultantReview review = new ConsultantReview();
//    review.setUser(consultant);
//    review.setReviewMarks(request.getRating());
//    review.setReviewNote(request.getFeedback());
//
//    ConsultantReview savedReview = consultantReviewRepository.save(review);
//
//    // Beautiful Email Logic
//    try {
//        String stars = request.getRating() >= 4.5 ? "★★★★★" :
//                request.getRating() >= 3.5 ? "★★★★☆" :
//                        request.getRating() >= 2.5 ? "★★★☆☆" :
//                                request.getRating() >= 1.5 ? "★★☆☆☆" : "★☆☆☆☆";
//
//        String ratingColor = request.getRating() >= 4.0 ? "#28a745" :
//                request.getRating() >= 3.0 ? "#ffc107" : "#dc3545";
//
//        String html = "<!DOCTYPE html>\n" +
//                "<html>\n" +
//                "<head>\n" +
//                "<style>\n" +
//                "body { font-family: Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }\n" +
//                ".container { max-width: 500px; margin: 30px auto; background: white; border-radius: 16px; overflow: hidden; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }\n" +
//                ".header { background: linear-gradient(135deg, #667eea, #764ba2); padding: 25px; text-align: center; }\n" +
//                ".header h1 { color: white; margin: 0; font-size: 24px; }\n" +
//                ".content { padding: 25px; }\n" +
//                ".rating-box { background: #f8f9fa; border-radius: 12px; padding: 20px; text-align: center; margin: 20px 0; }\n" +
//                ".stars { font-size: 30px; color: #ffc107; }\n" +
//                ".rating-value { font-size: 42px; font-weight: bold; color: " + ratingColor + "; }\n" +
//                ".feedback-box { background: #e8f4fd; border-radius: 12px; padding: 15px; margin-top: 15px; }\n" +
//                ".footer { background: #f8f9fa; padding: 15px; text-align: center; font-size: 12px; color: #888; }\n" +
//                "</style>\n" +
//                "</head>\n" +
//                "<body>\n" +
//                "<div class='container'>\n" +
//                "<div class='header'>\n" +
//                "<h1>⭐ New Rating Received!</h1>\n" +
//                "</div>\n" +
//                "<div class='content'>\n" +
//                "<h3>Hello <strong>" + consultant.getName() + "</strong>,</h3>\n" +
//                "<p><strong>" + manager.getName() + "</strong> has rated your performance.</p>\n" +
//                "<div class='rating-box'>\n" +
//                "<div class='stars'>" + stars + "</div>\n" +
//                "<div class='rating-value'>" + request.getRating() + " / 5.0</div>\n" +
//                "</div>\n" +
//                "<div class='feedback-box'>\n" +
//                "<strong>📝 Feedback:</strong><br>\n" +
//                "\"" + (request.getFeedback() != null ? request.getFeedback() : "No additional comments") + "\"\n" +
//                "</div>\n" +
//                "</div>\n" +
//                "<div class='footer'>\n" +
//                "<p>ShardaOS - Project Management System</p>\n" +
//                "<p>Keep up the great work! 💪</p>\n" +
//                "</div>\n" +
//                "</div>\n" +
//                "</body>\n" +
//                "</html>";
//
//        emailService.sendEmail(consultant.getEmail(), "⭐ You've Received a New Rating!", html);
//        System.out.println("✅ Email sent to: " + consultant.getEmail());
//
//    } catch (Exception e) {
//        System.out.println("❌ Email error: " + e.getMessage());
//    }
//
//    return ConsultantRatingResponseDTO.builder()
//            .reviewId(savedReview.getReviewId())
//            .consultantId(request.getConsultantId())
//            .consultantName(consultant.getName())
//            .reviewerId(managerId)
//            .reviewerName(manager.getName())
//            .reviewMarks(request.getRating())
//            .reviewNote(request.getFeedback())
//            .createdAt(savedReview.getCreatedAt())
//            .build();
//}
//
//
//    public List<ConsultantWithRatingDTO> getAllConsultantsWithRatings(Long managerId, Long departmentId) {
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);
//
//        return consultants.stream().map(c -> {
//            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(c.getUser().getUserId());
//            Integer reviewCount = consultantReviewRepository.getReviewCountByUserId(c.getUser().getUserId());
//
//            return ConsultantWithRatingDTO.builder()
//                    .consultantId(c.getConsultantId())
//                    .userId(c.getUser().getUserId())
//                    .name(c.getUser().getName())
//                    .email(c.getUser().getEmail())
//                    .photoUrl(c.getUser().getPhotoUrl())
//                    .skill(c.getSkill())
//                    .experience(c.getExperience())
//                    .averageRating(avgRating != null ? avgRating : 0.0)
//                    .totalReviews(reviewCount != null ? reviewCount : 0)
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
//    public List<ConsultantWithRatingDTO> getMyDepartmentConsultants(Long managerId) {
//        if (!isManager(managerId)) {
//            throw new RuntimeException("Only managers can view department consultants");
//        }
//
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(managerId)
//                .orElseThrow(() -> new RuntimeException("Employee not found"));
//
//        if (employee.getDepartmentRef() == null) {
//            throw new RuntimeException("Manager not assigned to any department");
//        }
//
//        Long departmentId = employee.getDepartmentRef().getDepartmentId();
//
//        List<ConsultantDetails> consultants = consultantDetailsRepository.findByDepartmentDepartmentId(departmentId);
//
//        return consultants.stream().map(c -> {
//            Double avgRating = consultantReviewRepository.getAverageRatingByUserId(c.getUser().getUserId());
//            Integer reviewCount = consultantReviewRepository.getReviewCountByUserId(c.getUser().getUserId());
//
//            return ConsultantWithRatingDTO.builder()
//                    .consultantId(c.getConsultantId())
//                    .userId(c.getUser().getUserId())
//                    .name(c.getUser().getName())
//                    .email(c.getUser().getEmail())
//                    .photoUrl(c.getUser().getPhotoUrl())
//                    .skill(c.getSkill())
//                    .experience(c.getExperience())
//                    .averageRating(avgRating != null ? avgRating : 0.0)
//                    .totalReviews(reviewCount != null ? reviewCount : 0)
//                    .build();
//        }).collect(Collectors.toList());
//    }
//
//    // ==================== HELPER METHODS ====================
//
//    private boolean isManager(Long userId) {
//        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
//                .filter(ut -> "MANAGER".equals(ut.getStype()))
//                .isPresent();
//    }
//
//    private boolean isConsultant(Long userId) {
//        return userTypeRepository.findByUserUserIdAndPtype(userId, "CONSULTANT").isPresent();
//    }
//
//    // ==================== EMAIL METHODS ====================
//
//    private void sendRequestEmail(ConsultantDetails consultant, ProjectDetails project) {
//        Double avgRating = consultantReviewRepository.getAverageRatingByUserId(consultant.getUser().getUserId());
//        String subject = "📋 New Consultant Request: " + project.getProjectName();
//        String html = String.format("""
//            <html><body>
//            <h2>New Request Received</h2>
//            <p><strong>Consultant:</strong> %s</p>
//            <p><strong>Project:</strong> %s</p>
//            <p><strong>Department:</strong> %s</p>
//            <p><strong>Skills:</strong> %s</p>
//            <p><strong>Experience:</strong> %s</p>
//            <p><strong>Rating:</strong> %.1f/5.0</p>
//            <p>Please login to view details and assign.</p>
//            </body></html>
//            """,
//                consultant.getUser().getName(),
//                project.getProjectName(),
//                consultant.getDepartment().getDepartmentName(),
//                consultant.getSkill(),
//                consultant.getExperience(),
//                avgRating != null ? avgRating : 0.0
//        );
//        emailService.sendEmail(project.getAssignedManager().getEmail(), subject, html);
//    }
//
//    private void sendRatingEmailToConsultant(User consultant, User manager, Double rating, String feedback) {
//        String subject = "⭐ You've Received a New Rating!";
//        String stars = rating >= 4.5 ? "★★★★★" : rating >= 3.5 ? "★★★★☆" : rating >= 2.5 ? "★★★☆☆" : rating >= 1.5 ? "★★☆☆☆" : "★☆☆☆☆";
//        String ratingColor = rating >= 4.0 ? "#28a745" : rating >= 3.0 ? "#ffc107" : "#dc3545";
//
//        String html = String.format("""
//            <!DOCTYPE html>
//            <html>
//            <head>
//                <style>
//                    body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
//                    .container { max-width: 550px; margin: 30px auto; background: white; border-radius: 16px; box-shadow: 0 5px 15px rgba(0,0,0,0.1); }
//                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 25px; text-align: center; border-radius: 16px 16px 0 0; }
//                    .header h1 { color: white; margin: 0; font-size: 26px; }
//                    .content { padding: 30px; }
//                    .rating-box { background: #f8f9fa; border-radius: 12px; padding: 20px; text-align: center; margin: 20px 0; }
//                    .stars { font-size: 32px; letter-spacing: 4px; color: #ffc107; }
//                    .rating-value { font-size: 48px; font-weight: bold; color: %s; }
//                    .feedback-box { background: #e8f4fd; border-radius: 12px; padding: 15px; margin-top: 15px; }
//                    .btn { display: inline-block; background: #667eea; color: white; padding: 12px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; margin-top: 20px; }
//                    .footer { background: #f8f9fa; padding: 15px; text-align: center; font-size: 12px; color: #888; border-top: 1px solid #eee; border-radius: 0 0 16px 16px; }
//                </style>
//            </head>
//            <body>
//                <div class="container">
//                    <div class="header">
//                        <h1>⭐ New Rating Received!</h1>
//                    </div>
//                    <div class="content">
//                        <h3>Hello <strong>%s</strong>,</h3>
//                        <p><strong>%s</strong> has rated your performance.</p>
//                        <div class="rating-box">
//                            <div class="stars">%s</div>
//                            <div class="rating-value">%.1f / 5.0</div>
//                        </div>
//                        <div class="feedback-box">
//                            <p><strong>📝 Feedback:</strong></p>
//                            <p>"%s"</p>
//                        </div>
//                        <div style="text-align: center;">
//                            <a href="https://shardaos.com/consultant/ratings" class="btn">View All My Ratings</a>
//                        </div>
//                    </div>
//                    <div class="footer">
//                        <p>ShardaOS - Project Management System</p>
//                        <p>Keep up the great work! 💪</p>
//                    </div>
//                </div>
//            </body>
//            </html>
//            """,
//                ratingColor,
//                consultant.getName(),
//                manager.getName(),
//                stars,
//                rating,
//                feedback != null ? feedback : "No additional comments"
//        );
//
//        emailService.sendEmail(consultant.getEmail(), subject, html);
//    }
//}