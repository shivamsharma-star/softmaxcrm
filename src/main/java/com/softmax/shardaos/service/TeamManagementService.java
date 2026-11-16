package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.input.TeamCreateRequest;
import com.softmax.shardaos.dto.output.TeamMemberDTO;
import com.softmax.shardaos.dto.output.TeamResponseDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class TeamManagementService {

    @Autowired
    private TeamDetailsRepository teamRepository;

    @Autowired
    private TeamMembersRepository teamMembersRepository;

    @Autowired
    private ProjectDetailsRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    @Autowired
    private ConsultantDetailsRepository consultantDetailsRepository;

    @Autowired
    private EmailService emailService;

    // ==================== HELPER METHODS ====================

    private boolean isManager(Long userId) {
        System.out.println("🔍 [Team] isManager check for userId: " + userId);
        var userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(userId, "EMP");
        if (userTypeOpt.isEmpty()) {
            System.out.println("❌ [Team] No EMP role found");
            return false;
        }
        boolean isManager = "MANAGER".equals(userTypeOpt.get().getStype());
        System.out.println("✅ [Team] isManager: " + isManager);
        return isManager;
    }

    private boolean isDirector(Long userId) {
        var userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(userId, "EMP");
        return userTypeOpt.isPresent() && "DIRECTOR".equals(userTypeOpt.get().getStype());
    }

    private Long getManagerDepartmentId(Long managerUserId) {
        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(managerUserId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        if (employee.getDepartmentRef() == null) {
            throw new RuntimeException("Manager not assigned to any department");
        }
        return employee.getDepartmentRef().getDepartmentId();
    }

    // ==================== CREATE TEAM ====================

    @Transactional
    public TeamResponseDTO createTeam(Long managerUserId, TeamCreateRequest request) {
        System.out.println("🔍 [Team] ========== CREATE TEAM START ==========");
        System.out.println("📌 [Team] managerUserId: " + managerUserId);
        System.out.println("📌 [Team] projectId: " + request.getProjectId());
        System.out.println("📌 [Team] teamName: " + request.getTeamName());
        System.out.println("📌 [Team] memberIds: " + request.getMemberIds());

        if (!isManager(managerUserId)) {
            throw new RuntimeException("Only managers can create teams");
        }

        ProjectDetails project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + request.getProjectId()));
        System.out.println("✅ [Team] Project found: " + project.getProjectName());

        if (project.getAssignedManager() == null ||
                !project.getAssignedManager().getUserId().equals(managerUserId)) {
            throw new RuntimeException("You can only create teams for projects assigned to you");
        }
        System.out.println("✅ [Team] Manager is assigned to this project");

        User manager = userRepository.findById(managerUserId).get();

        TeamDetails team = new TeamDetails();
        team.setProject(project);
        team.setTeamName(request.getTeamName());
        team.setManager(manager);
        team = teamRepository.save(team);
        System.out.println("✅ [Team] Team saved with ID: " + team.getTeamId());

        // Add members if provided
        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            System.out.println("📌 [Team] Adding " + request.getMemberIds().size() + " members");
            for (int i = 0; i < request.getMemberIds().size(); i++) {
                Long memberId = request.getMemberIds().get(i);
                User member = userRepository.findById(memberId)
                        .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));

                TeamMembers teamMember = new TeamMembers();
                teamMember.setTeam(team);
                teamMember.setMember(member);
                teamMember.setRole(request.getMemberRoles() != null && i < request.getMemberRoles().size()
                        ? request.getMemberRoles().get(i) : "Consultant");
                teamMembersRepository.save(teamMember);
                System.out.println("  ✅ [Team] Member added: " + member.getName() + " (ID: " + memberId + ")");

                // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
                sendTeamAssignmentEmailAsync(member, team, manager);
            }
        } else {
            System.out.println("⚠️ [Team] No members to add");
        }

        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
        sendTeamCreatedEmailAsync(manager, team, project);

        System.out.println("✅ [Team] ========== RESPONSE SENT IMMEDIATELY ==========");
        System.out.println("✅ [Team] Mail will be sent in background");
        System.out.println("🔍 [Team] ========== CREATE TEAM END ==========");

        // 🔥 PHAHLE RESPONSE BHEJO
        return convertToDTO(team);
    }

    // ==================== ADD MEMBER ====================

    @Transactional
    public TeamResponseDTO addTeamMember(Long managerUserId, Long teamId, Long memberId, String role) {
        System.out.println("🔍 [Team] addTeamMember - managerId: " + managerUserId + ", teamId: " + teamId + ", memberId: " + memberId);

        if (!isManager(managerUserId)) {
            throw new RuntimeException("Only managers can add team members");
        }

        TeamDetails team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));

        if (!team.getManager().getUserId().equals(managerUserId)) {
            throw new RuntimeException("You can only add members to your own teams");
        }

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));

        User manager = userRepository.findById(managerUserId).get();

        // Check if already a member
        List<TeamMembers> existingMembers = teamMembersRepository.findByTeamTeamId(teamId);
        boolean alreadyMember = existingMembers.stream()
                .anyMatch(m -> m.getMember().getUserId().equals(memberId));

        if (alreadyMember) {
            throw new RuntimeException("User is already a member of this team");
        }

        TeamMembers teamMember = new TeamMembers();
        teamMember.setTeam(team);
        teamMember.setMember(member);
        teamMember.setRole(role != null ? role : "Consultant");
        teamMembersRepository.save(teamMember);
        System.out.println("✅ [Team] Member added: " + member.getName());

        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
        sendTeamAssignmentEmailAsync(member, team, manager);

        System.out.println("✅ [Team] Response sent, mail will go in background");
        return convertToDTO(team);
    }

    // ==================== REMOVE MEMBER ====================

    @Transactional
    public void removeTeamMember(Long managerUserId, Long teamId, Long memberId) {
        System.out.println("🔍 [Team] removeTeamMember - managerId: " + managerUserId + ", teamId: " + teamId + ", memberId: " + memberId);

        if (!isManager(managerUserId)) {
            throw new RuntimeException("Only managers can remove team members");
        }

        TeamDetails team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        if (!team.getManager().getUserId().equals(managerUserId)) {
            throw new RuntimeException("You can only remove members from your own teams");
        }

        List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(teamId);
        members.stream()
                .filter(m -> m.getMember().getUserId().equals(memberId))
                .findFirst()
                .ifPresent(teamMembersRepository::delete);

        System.out.println("✅ [Team] Member removed: " + memberId);
    }

    // ==================== DELETE TEAM ====================

    @Transactional
    public void deleteTeam(Long managerUserId, Long teamId) {
        System.out.println("🔍 [Team] deleteTeam - managerId: " + managerUserId + ", teamId: " + teamId);

        if (!isManager(managerUserId)) {
            throw new RuntimeException("Only managers can delete teams");
        }

        TeamDetails team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        if (!team.getManager().getUserId().equals(managerUserId)) {
            throw new RuntimeException("You can only delete your own teams");
        }

        teamMembersRepository.deleteByTeamTeamId(teamId);
        teamRepository.delete(team);
        System.out.println("✅ [Team] Team deleted: " + teamId);
    }

    // ==================== GET PROJECT TEAMS ====================

    public List<TeamResponseDTO> getProjectTeams(Long userId, Long projectId) {
        System.out.println("🔍 [Team] getProjectTeams - userId: " + userId + ", projectId: " + projectId);

        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        boolean hasAccess = false;

        if (project.getUploader().getUserId().equals(userId)) {
            hasAccess = true;
        }

        if (project.getAssignedManager() != null && project.getAssignedManager().getUserId().equals(userId)) {
            hasAccess = true;
        }

        if (isDirector(userId)) {
            Long directorDeptId = getManagerDepartmentId(userId);
            if (project.getDepartment() != null && project.getDepartment().getDepartmentId().equals(directorDeptId)) {
                hasAccess = true;
            }
        }

        List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(userId);
        boolean isTeamMember = userTeams.stream()
                .anyMatch(tm -> tm.getTeam().getProject().getProjectId().equals(projectId));
        if (isTeamMember) {
            hasAccess = true;
        }

        if (!hasAccess) {
            throw new RuntimeException("You don't have access to view this project's teams");
        }

        return teamRepository.findByProjectProjectId(projectId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ==================== GET MY TEAMS ====================

    public List<TeamResponseDTO> getMyTeams(Long managerUserId) {
        System.out.println("🔍 [Team] getMyTeams - managerId: " + managerUserId);

        if (!isManager(managerUserId)) {
            throw new RuntimeException("Only managers can view their teams");
        }

        List<TeamDetails> teams = teamRepository.findByManagerUserId(managerUserId);
        System.out.println("✅ [Team] Found " + teams.size() + " teams");

        return teams.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ==================== GET MY CONSULTANT TEAMS ====================

    public List<TeamResponseDTO> getMyConsultantTeams(Long consultantUserId) {
        System.out.println("🔍 [Team] getMyConsultantTeams - consultantId: " + consultantUserId);

        List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(consultantUserId);
        System.out.println("✅ [Team] Found " + userTeams.size() + " teams for consultant");

        return userTeams.stream()
                .map(tm -> convertToDTO(tm.getTeam()))
                .collect(Collectors.toList());
    }

    // ==================== CONVERT TO DTO ====================

    private TeamResponseDTO convertToDTO(TeamDetails team) {
        List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(team.getTeamId());
        List<TeamMemberDTO> memberDTOs = members.stream()
                .map(m -> TeamMemberDTO.builder()
                        .memberId(m.getMember().getUserId())
                        .memberName(m.getMember().getName())
                        .memberEmail(m.getMember().getEmail())
                        .photoUrl(m.getMember().getPhotoUrl())
                        .role(m.getRole())
                        .description(m.getDescription())
                        .build())
                .collect(Collectors.toList());

        return TeamResponseDTO.builder()
                .teamId(team.getTeamId())
                .teamName(team.getTeamName())
                .projectId(team.getProject().getProjectId())
                .projectName(team.getProject().getProjectName())
                .managerId(team.getManager().getUserId())
                .managerName(team.getManager().getName())
                .members(memberDTOs)
                .createdAt(team.getCreatedAt())
                .build();
    }

    // ==================== ASYNC EMAIL METHODS (BACKGROUND ME BHEJEGA) ====================

    @Async
    public void sendTeamCreatedEmailAsync(User manager, TeamDetails team, ProjectDetails project) {
        try {
            System.out.println("📧 [Team] Background mail starting for manager: " + manager.getEmail());

            String subject = "🎉 Team Created Successfully - " + team.getTeamName() + " - Softmax Technologies and IT Services";

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
                        .btn { display: inline-block; background: linear-gradient(135deg,#11998e 0%,#38ef7d 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(17,153,142,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #11998e; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>🎉 Team Created Successfully!</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">Your team <strong>'%s'</strong> has been created successfully for project <strong>'%s'</strong>.</p>
                            <div class="info-box">
                                <p>📋 <strong>Team Details:</strong></p>
                                <p>🆔 <strong>Team ID:</strong> %d</p>
                                <p>👥 <strong>Team Name:</strong> %s</p>
                                <p>📁 <strong>Project:</strong> %s</p>
                            </div>
                            <p style="color:#4a5568;">You can now add more members to your team from the dashboard.</p>
                            <div style="text-align:center;">
                                <a href="https://nexus.softmax.in" class="btn">🚀 View Teams</a>
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
                    manager.getName(),
                    team.getTeamName(),
                    project.getProjectName(),
                    team.getTeamId(),
                    team.getTeamName(),
                    project.getProjectName()
            );
            emailService.sendEmail(manager.getEmail(), subject, html);
            System.out.println("✅ [Team] Background mail sent to manager: " + manager.getEmail());
        } catch (Exception e) {
            System.err.println("❌ [Team] Background mail failed for manager: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Async
    public void sendTeamAssignmentEmailAsync(User member, TeamDetails team, User manager) {
        try {
            System.out.println("📧 [Team] Background mail starting for member: " + member.getEmail());

            String subject = "👋 You've been added to a team - " + team.getTeamName() + " - Softmax Technologies and IT Services";

            String html = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; margin: 0; padding: 0; }
                        .container { max-width: 600px; margin: 30px auto; background: white; border-radius: 18px; overflow: hidden; box-shadow: 0 12px 45px rgba(0,0,0,0.12); }
                        .header { background: linear-gradient(135deg, #2196F3 0%, #1a237e 100%); padding: 40px 30px; text-align: center; }
                        .header h1 { color: white; margin: 0; font-size: 26px; letter-spacing: 0.5px; }
                        .header p { color: rgba(255,255,255,0.95); margin: 10px 0 0; font-size: 14px; }
                        .content { padding: 40px 35px; }
                        .info-box { background: #e3f2fd; padding: 22px; border-radius: 12px; margin: 22px 0; border-left: 5px solid #2196F3; }
                        .info-box p { margin: 8px 0; color: #2d3748; font-size: 14px; }
                        .btn { display: inline-block; background: linear-gradient(135deg,#2196F3 0%,#1a237e 100%); color: white !important; padding: 14px 34px; text-decoration: none; border-radius: 50px; font-weight: 600; margin-top: 18px; font-size: 15px; box-shadow: 0 6px 18px rgba(33,150,243,0.35); }
                        .footer { background: #f8f9fa; padding: 22px; text-align: center; border-top: 1px solid #e0e0e0; font-size: 12px; color: #888; }
                        .footer a { color: #2196F3; text-decoration: none; font-weight: 600; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>👋 Welcome to the Team!</h1>
                            <p>Softmax Technologies and IT Services</p>
                        </div>
                        <div class="content">
                            <h2 style="color:#333;margin-top:0;">Dear <strong>%s</strong>,</h2>
                            <p style="color:#4a5568;font-size:15px;">You have been added to team <strong>'%s'</strong> by <strong>%s</strong>.</p>
                            <div class="info-box">
                                <p>📋 <strong>Team Details:</strong></p>
                                <p>👥 <strong>Team Name:</strong> %s</p>
                                <p>📁 <strong>Project:</strong> %s</p>
                                <p>👨‍💼 <strong>Team Manager:</strong> %s</p>
                            </div>
                            <p style="color:#4a5568;">You can now view and work on tasks assigned to this team.</p>
                            <div style="text-align:center;">
                                <a href="https://nexus.softmax.in" class="btn">🚀 View My Teams</a>
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
                    member.getName(),
                    team.getTeamName(),
                    manager.getName(),
                    team.getTeamName(),
                    team.getProject().getProjectName(),
                    manager.getName()
            );
            emailService.sendEmail(member.getEmail(), subject, html);
            System.out.println("✅ [Team] Background mail sent to member: " + member.getEmail());
        } catch (Exception e) {
            System.err.println("❌ [Team] Background mail failed for member: " + e.getMessage());
            e.printStackTrace();
        }
    }
}












//package com.softmax.shardaos.service;
//
//import com.softmax.shardaos.dto.input.TeamCreateRequest;
//import com.softmax.shardaos.dto.output.TeamMemberDTO;
//import com.softmax.shardaos.dto.output.TeamResponseDTO;
//import com.softmax.shardaos.models.*;
//import com.softmax.shardaos.repository.*;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//import java.util.stream.Collectors;
//
//@Service
//public class TeamManagementService {
//
//    @Autowired
//    private TeamDetailsRepository teamRepository;
//
//    @Autowired
//    private TeamMembersRepository teamMembersRepository;
//
//    @Autowired
//    private ProjectDetailsRepository projectRepository;
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
//    private ConsultantDetailsRepository consultantDetailsRepository;
//
//    @Autowired
//    private EmailService emailService;
//
//    // ==================== HELPER METHODS ====================
//
//    private boolean isManager(Long userId) {
//        System.out.println("🔍 [Team] isManager check for userId: " + userId);
//        var userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(userId, "EMP");
//        if (userTypeOpt.isEmpty()) {
//            System.out.println("❌ [Team] No EMP role found");
//            return false;
//        }
//        boolean isManager = "MANAGER".equals(userTypeOpt.get().getStype());
//        System.out.println("✅ [Team] isManager: " + isManager);
//        return isManager;
//    }
//
//    private boolean isDirector(Long userId) {
//        var userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(userId, "EMP");
//        return userTypeOpt.isPresent() && "DIRECTOR".equals(userTypeOpt.get().getStype());
//    }
//
//    private Long getManagerDepartmentId(Long managerUserId) {
//        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(managerUserId)
//                .orElseThrow(() -> new RuntimeException("Employee not found"));
//        if (employee.getDepartmentRef() == null) {
//            throw new RuntimeException("Manager not assigned to any department");
//        }
//        return employee.getDepartmentRef().getDepartmentId();
//    }
//
//    // ==================== CREATE TEAM ====================
//
//    @Transactional
//    public TeamResponseDTO createTeam(Long managerUserId, TeamCreateRequest request) {
//        System.out.println("🔍 [Team] ========== CREATE TEAM START ==========");
//        System.out.println("📌 [Team] managerUserId: " + managerUserId);
//        System.out.println("📌 [Team] projectId: " + request.getProjectId());
//        System.out.println("📌 [Team] teamName: " + request.getTeamName());
//        System.out.println("📌 [Team] memberIds: " + request.getMemberIds());
//
//        if (!isManager(managerUserId)) {
//            throw new RuntimeException("Only managers can create teams");
//        }
//
//        ProjectDetails project = projectRepository.findById(request.getProjectId())
//                .orElseThrow(() -> new RuntimeException("Project not found with id: " + request.getProjectId()));
//        System.out.println("✅ [Team] Project found: " + project.getProjectName());
//
//        if (project.getAssignedManager() == null ||
//                !project.getAssignedManager().getUserId().equals(managerUserId)) {
//            throw new RuntimeException("You can only create teams for projects assigned to you");
//        }
//        System.out.println("✅ [Team] Manager is assigned to this project");
//
//        User manager = userRepository.findById(managerUserId).get();
//
//        TeamDetails team = new TeamDetails();
//        team.setProject(project);
//        team.setTeamName(request.getTeamName());
//        team.setManager(manager);
//        team = teamRepository.save(team);
//        System.out.println("✅ [Team] Team saved with ID: " + team.getTeamId());
//
//        // Add members if provided
//        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
//            System.out.println("📌 [Team] Adding " + request.getMemberIds().size() + " members");
//            for (int i = 0; i < request.getMemberIds().size(); i++) {
//                Long memberId = request.getMemberIds().get(i);
//                User member = userRepository.findById(memberId)
//                        .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));
//
//                TeamMembers teamMember = new TeamMembers();
//                teamMember.setTeam(team);
//                teamMember.setMember(member);
//                teamMember.setRole(request.getMemberRoles() != null && i < request.getMemberRoles().size()
//                        ? request.getMemberRoles().get(i) : "Consultant");
//                teamMembersRepository.save(teamMember);
//                System.out.println("  ✅ [Team] Member added: " + member.getName() + " (ID: " + memberId + ")");
//
//                // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
//                sendTeamAssignmentEmailAsync(member, team, manager);
//            }
//        } else {
//            System.out.println("⚠️ [Team] No members to add");
//        }
//
//        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
//        sendTeamCreatedEmailAsync(manager, team, project);
//
//        System.out.println("✅ [Team] ========== RESPONSE SENT IMMEDIATELY ==========");
//        System.out.println("✅ [Team] Mail will be sent in background");
//        System.out.println("🔍 [Team] ========== CREATE TEAM END ==========");
//
//        // 🔥 PHAHLE RESPONSE BHEJO
//        return convertToDTO(team);
//    }
//
//    // ==================== ADD MEMBER ====================
//
//    @Transactional
//    public TeamResponseDTO addTeamMember(Long managerUserId, Long teamId, Long memberId, String role) {
//        System.out.println("🔍 [Team] addTeamMember - managerId: " + managerUserId + ", teamId: " + teamId + ", memberId: " + memberId);
//
//        if (!isManager(managerUserId)) {
//            throw new RuntimeException("Only managers can add team members");
//        }
//
//        TeamDetails team = teamRepository.findById(teamId)
//                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));
//
//        if (!team.getManager().getUserId().equals(managerUserId)) {
//            throw new RuntimeException("You can only add members to your own teams");
//        }
//
//        User member = userRepository.findById(memberId)
//                .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));
//
//        User manager = userRepository.findById(managerUserId).get();
//
//        // Check if already a member
//        List<TeamMembers> existingMembers = teamMembersRepository.findByTeamTeamId(teamId);
//        boolean alreadyMember = existingMembers.stream()
//                .anyMatch(m -> m.getMember().getUserId().equals(memberId));
//
//        if (alreadyMember) {
//            throw new RuntimeException("User is already a member of this team");
//        }
//
//        TeamMembers teamMember = new TeamMembers();
//        teamMember.setTeam(team);
//        teamMember.setMember(member);
//        teamMember.setRole(role != null ? role : "Consultant");
//        teamMembersRepository.save(teamMember);
//        System.out.println("✅ [Team] Member added: " + member.getName());
//
//        // 🔥 MAIL BACKGROUND ME BHEJEGE - RESPONSE KE BAAD
//        sendTeamAssignmentEmailAsync(member, team, manager);
//
//        System.out.println("✅ [Team] Response sent, mail will go in background");
//        return convertToDTO(team);
//    }
//
//    // ==================== REMOVE MEMBER ====================
//
//    @Transactional
//    public void removeTeamMember(Long managerUserId, Long teamId, Long memberId) {
//        System.out.println("🔍 [Team] removeTeamMember - managerId: " + managerUserId + ", teamId: " + teamId + ", memberId: " + memberId);
//
//        if (!isManager(managerUserId)) {
//            throw new RuntimeException("Only managers can remove team members");
//        }
//
//        TeamDetails team = teamRepository.findById(teamId)
//                .orElseThrow(() -> new RuntimeException("Team not found"));
//
//        if (!team.getManager().getUserId().equals(managerUserId)) {
//            throw new RuntimeException("You can only remove members from your own teams");
//        }
//
//        List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(teamId);
//        members.stream()
//                .filter(m -> m.getMember().getUserId().equals(memberId))
//                .findFirst()
//                .ifPresent(teamMembersRepository::delete);
//
//        System.out.println("✅ [Team] Member removed: " + memberId);
//    }
//
//    // ==================== DELETE TEAM ====================
//
//    @Transactional
//    public void deleteTeam(Long managerUserId, Long teamId) {
//        System.out.println("🔍 [Team] deleteTeam - managerId: " + managerUserId + ", teamId: " + teamId);
//
//        if (!isManager(managerUserId)) {
//            throw new RuntimeException("Only managers can delete teams");
//        }
//
//        TeamDetails team = teamRepository.findById(teamId)
//                .orElseThrow(() -> new RuntimeException("Team not found"));
//
//        if (!team.getManager().getUserId().equals(managerUserId)) {
//            throw new RuntimeException("You can only delete your own teams");
//        }
//
//        teamMembersRepository.deleteByTeamTeamId(teamId);
//        teamRepository.delete(team);
//        System.out.println("✅ [Team] Team deleted: " + teamId);
//    }
//
//    // ==================== GET PROJECT TEAMS ====================
//
//    public List<TeamResponseDTO> getProjectTeams(Long userId, Long projectId) {
//        System.out.println("🔍 [Team] getProjectTeams - userId: " + userId + ", projectId: " + projectId);
//
//        ProjectDetails project = projectRepository.findById(projectId)
//                .orElseThrow(() -> new RuntimeException("Project not found"));
//
//        boolean hasAccess = false;
//
//        if (project.getUploader().getUserId().equals(userId)) {
//            hasAccess = true;
//        }
//
//        if (project.getAssignedManager() != null && project.getAssignedManager().getUserId().equals(userId)) {
//            hasAccess = true;
//        }
//
//        if (isDirector(userId)) {
//            Long directorDeptId = getManagerDepartmentId(userId);
//            if (project.getDepartment() != null && project.getDepartment().getDepartmentId().equals(directorDeptId)) {
//                hasAccess = true;
//            }
//        }
//
//        List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(userId);
//        boolean isTeamMember = userTeams.stream()
//                .anyMatch(tm -> tm.getTeam().getProject().getProjectId().equals(projectId));
//        if (isTeamMember) {
//            hasAccess = true;
//        }
//
//        if (!hasAccess) {
//            throw new RuntimeException("You don't have access to view this project's teams");
//        }
//
//        return teamRepository.findByProjectProjectId(projectId)
//                .stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//    }
//
//    // ==================== GET MY TEAMS ====================
//
//    public List<TeamResponseDTO> getMyTeams(Long managerUserId) {
//        System.out.println("🔍 [Team] getMyTeams - managerId: " + managerUserId);
//
//        if (!isManager(managerUserId)) {
//            throw new RuntimeException("Only managers can view their teams");
//        }
//
//        List<TeamDetails> teams = teamRepository.findByManagerUserId(managerUserId);
//        System.out.println("✅ [Team] Found " + teams.size() + " teams");
//
//        return teams.stream()
//                .map(this::convertToDTO)
//                .collect(Collectors.toList());
//    }
//
//    // ==================== GET MY CONSULTANT TEAMS ====================
//
//    public List<TeamResponseDTO> getMyConsultantTeams(Long consultantUserId) {
//        System.out.println("🔍 [Team] getMyConsultantTeams - consultantId: " + consultantUserId);
//
//        List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(consultantUserId);
//        System.out.println("✅ [Team] Found " + userTeams.size() + " teams for consultant");
//
//        return userTeams.stream()
//                .map(tm -> convertToDTO(tm.getTeam()))
//                .collect(Collectors.toList());
//    }
//
//    // ==================== CONVERT TO DTO ====================
//
//    private TeamResponseDTO convertToDTO(TeamDetails team) {
//        List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(team.getTeamId());
//        List<TeamMemberDTO> memberDTOs = members.stream()
//                .map(m -> TeamMemberDTO.builder()
//                        .memberId(m.getMember().getUserId())
//                        .memberName(m.getMember().getName())
//                        .memberEmail(m.getMember().getEmail())
//                        .photoUrl(m.getMember().getPhotoUrl())
//                        .role(m.getRole())
//                        .description(m.getDescription())
//                        .build())
//                .collect(Collectors.toList());
//
//        return TeamResponseDTO.builder()
//                .teamId(team.getTeamId())
//                .teamName(team.getTeamName())
//                .projectId(team.getProject().getProjectId())
//                .projectName(team.getProject().getProjectName())
//                .managerId(team.getManager().getUserId())
//                .managerName(team.getManager().getName())
//                .members(memberDTOs)
//                .createdAt(team.getCreatedAt())
//                .build();
//    }
//
//    // ==================== ASYNC EMAIL METHODS (BACKGROUND ME BHEJEGA) ====================
//
//    @Async
//    public void sendTeamCreatedEmailAsync(User manager, TeamDetails team, ProjectDetails project) {
//        try {
//            System.out.println("📧 [Team] Background mail starting for manager: " + manager.getEmail());
//
//            String subject = "🎉 Team Created Successfully - " + team.getTeamName();
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: #4CAF50;'>🎉 Team Created Successfully!</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>Your team <strong>'%s'</strong> has been created successfully for project <strong>'%s'</strong>.</p>
//                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>📋 Team Details:</strong></p>
//                            <p>• Team ID: %d</p>
//                            <p>• Team Name: %s</p>
//                            <p>• Project: %s</p>
//                        </div>
//                        <p>You can now add more members to your team from the dashboard.</p>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>Team Gaurion Bharat</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    manager.getName(),
//                    team.getTeamName(),
//                    project.getProjectName(),
//                    team.getTeamId(),
//                    team.getTeamName(),
//                    project.getProjectName()
//            );
//            emailService.sendEmail(manager.getEmail(), subject, html);
//            System.out.println("✅ [Team] Background mail sent to manager: " + manager.getEmail());
//        } catch (Exception e) {
//            System.err.println("❌ [Team] Background mail failed for manager: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    @Async
//    public void sendTeamAssignmentEmailAsync(User member, TeamDetails team, User manager) {
//        try {
//            System.out.println("📧 [Team] Background mail starting for member: " + member.getEmail());
//
//            String subject = "👋 You've been added to a team - " + team.getTeamName();
//            String html = String.format("""
//                <html>
//                <body style='font-family: Arial, sans-serif; padding: 20px;'>
//                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
//                        <h2 style='color: #2196F3;'>👋 Welcome to the Team!</h2>
//                        <p>Dear <strong>%s</strong>,</p>
//                        <p>You have been added to team <strong>'%s'</strong> by <strong>%s</strong>.</p>
//                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
//                            <p><strong>📋 Team Details:</strong></p>
//                            <p>• Team Name: %s</p>
//                            <p>• Project: %s</p>
//                            <p>• Team Manager: %s</p>
//                        </div>
//                        <p>You can now view and work on tasks assigned to this team.</p>
//                        <hr style='margin: 20px 0;'>
//                        <p style='color: #777; font-size: 12px;'>Team Gaurion Bharat</p>
//                    </div>
//                </body>
//                </html>
//                """,
//                    member.getName(),
//                    team.getTeamName(),
//                    manager.getName(),
//                    team.getTeamName(),
//                    team.getProject().getProjectName(),
//                    manager.getName()
//            );
//            emailService.sendEmail(member.getEmail(), subject, html);
//            System.out.println("✅ [Team] Background mail sent to member: " + member.getEmail());
//        } catch (Exception e) {
//            System.err.println("❌ [Team] Background mail failed for member: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//}
//
//
//
//
//
////package com.softmax.shardaos.service;
////
////import com.softmax.shardaos.dto.input.TeamCreateRequest;
////import com.softmax.shardaos.dto.output.TeamMemberDTO;
////import com.softmax.shardaos.dto.output.TeamResponseDTO;
////import com.softmax.shardaos.models.*;
////import com.softmax.shardaos.repository.*;
////import org.springframework.beans.factory.annotation.Autowired;
////import org.springframework.stereotype.Service;
////import org.springframework.transaction.annotation.Transactional;
////
////import java.util.List;
////import java.util.stream.Collectors;
////
////@Service
////public class TeamManagementService {
////
////    @Autowired
////    private TeamDetailsRepository teamRepository;
////
////    @Autowired
////    private TeamMembersRepository teamMembersRepository;
////
////    @Autowired
////    private ProjectDetailsRepository projectRepository;
////
////    @Autowired
////    private UserRepository userRepository;
////
////    @Autowired
////    private UserTypeRepository userTypeRepository;
////
////    @Autowired
////    private EmployeeDetailsRepository employeeDetailsRepository;
////
////    @Autowired
////    private ConsultantDetailsRepository consultantDetailsRepository;
////
////    @Autowired
////    private EmailService emailService;
////
////    // ==================== HELPER METHODS ====================
////
////    private boolean isManager(Long userId) {
////        System.out.println("🔍 [Team] isManager check for userId: " + userId);
////        var userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(userId, "EMP");
////        if (userTypeOpt.isEmpty()) {
////            System.out.println("❌ [Team] No EMP role found");
////            return false;
////        }
////        boolean isManager = "MANAGER".equals(userTypeOpt.get().getStype());
////        System.out.println("✅ [Team] isManager: " + isManager);
////        return isManager;
////    }
////
////    private boolean isDirector(Long userId) {
////        var userTypeOpt = userTypeRepository.findByUserUserIdAndPtype(userId, "EMP");
////        return userTypeOpt.isPresent() && "DIRECTOR".equals(userTypeOpt.get().getStype());
////    }
////
////    private Long getManagerDepartmentId(Long managerUserId) {
////        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(managerUserId)
////                .orElseThrow(() -> new RuntimeException("Employee not found"));
////        if (employee.getDepartmentRef() == null) {
////            throw new RuntimeException("Manager not assigned to any department");
////        }
////        return employee.getDepartmentRef().getDepartmentId();
////    }
////
////    // ==================== CREATE TEAM ====================
////
////    @Transactional
////    public TeamResponseDTO createTeam(Long managerUserId, TeamCreateRequest request) {
////        System.out.println("🔍 [Team] ========== CREATE TEAM START ==========");
////        System.out.println("📌 [Team] managerUserId: " + managerUserId);
////        System.out.println("📌 [Team] projectId: " + request.getProjectId());
////        System.out.println("📌 [Team] teamName: " + request.getTeamName());
////        System.out.println("📌 [Team] memberIds: " + request.getMemberIds());
////
////        if (!isManager(managerUserId)) {
////            throw new RuntimeException("Only managers can create teams");
////        }
////
////        ProjectDetails project = projectRepository.findById(request.getProjectId())
////                .orElseThrow(() -> new RuntimeException("Project not found with id: " + request.getProjectId()));
////        System.out.println("✅ [Team] Project found: " + project.getProjectName());
////
////        if (project.getAssignedManager() == null ||
////                !project.getAssignedManager().getUserId().equals(managerUserId)) {
////            throw new RuntimeException("You can only create teams for projects assigned to you");
////        }
////        System.out.println("✅ [Team] Manager is assigned to this project");
////
////        User manager = userRepository.findById(managerUserId).get();
////
////        TeamDetails team = new TeamDetails();
////        team.setProject(project);
////        team.setTeamName(request.getTeamName());
////        team.setManager(manager);
////        team = teamRepository.save(team);
////        System.out.println("✅ [Team] Team saved with ID: " + team.getTeamId());
////
////        // Add members if provided
////        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
////            System.out.println("📌 [Team] Adding " + request.getMemberIds().size() + " members");
////            for (int i = 0; i < request.getMemberIds().size(); i++) {
////                Long memberId = request.getMemberIds().get(i);
////                User member = userRepository.findById(memberId)
////                        .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));
////
////                TeamMembers teamMember = new TeamMembers();
////                teamMember.setTeam(team);
////                teamMember.setMember(member);
////                teamMember.setRole(request.getMemberRoles() != null && i < request.getMemberRoles().size()
////                        ? request.getMemberRoles().get(i) : "Consultant");
////                teamMembersRepository.save(teamMember);
////                System.out.println("  ✅ [Team] Member added: " + member.getName() + " (ID: " + memberId + ")");
////
////                // Send email to member
////                sendTeamAssignmentEmail(member, team, manager);
////            }
////        } else {
////            System.out.println("⚠️ [Team] No members to add");
////        }
////
////        // Send email to manager
////        sendTeamCreatedEmail(manager, team, project);
////        System.out.println("✅ [Team] Email sent to manager");
////
////        System.out.println("🔍 [Team] ========== CREATE TEAM END ==========");
////        return convertToDTO(team);
////    }
////
////    // ==================== ADD MEMBER ====================
////
////    @Transactional
////    public TeamResponseDTO addTeamMember(Long managerUserId, Long teamId, Long memberId, String role) {
////        System.out.println("🔍 [Team] addTeamMember - managerId: " + managerUserId + ", teamId: " + teamId + ", memberId: " + memberId);
////
////        if (!isManager(managerUserId)) {
////            throw new RuntimeException("Only managers can add team members");
////        }
////
////        TeamDetails team = teamRepository.findById(teamId)
////                .orElseThrow(() -> new RuntimeException("Team not found with id: " + teamId));
////
////        if (!team.getManager().getUserId().equals(managerUserId)) {
////            throw new RuntimeException("You can only add members to your own teams");
////        }
////
////        User member = userRepository.findById(memberId)
////                .orElseThrow(() -> new RuntimeException("Member not found: " + memberId));
////
////        User manager = userRepository.findById(managerUserId).get();
////
////        // Check if already a member
////        List<TeamMembers> existingMembers = teamMembersRepository.findByTeamTeamId(teamId);
////        boolean alreadyMember = existingMembers.stream()
////                .anyMatch(m -> m.getMember().getUserId().equals(memberId));
////
////        if (alreadyMember) {
////            throw new RuntimeException("User is already a member of this team");
////        }
////
////        TeamMembers teamMember = new TeamMembers();
////        teamMember.setTeam(team);
////        teamMember.setMember(member);
////        teamMember.setRole(role != null ? role : "Consultant");
////        teamMembersRepository.save(teamMember);
////        System.out.println("✅ [Team] Member added: " + member.getName());
////
////        // Send email to new member
////        sendTeamAssignmentEmail(member, team, manager);
////
////        return convertToDTO(team);
////    }
////
////    // ==================== REMOVE MEMBER ====================
////
////    @Transactional
////    public void removeTeamMember(Long managerUserId, Long teamId, Long memberId) {
////        System.out.println("🔍 [Team] removeTeamMember - managerId: " + managerUserId + ", teamId: " + teamId + ", memberId: " + memberId);
////
////        if (!isManager(managerUserId)) {
////            throw new RuntimeException("Only managers can remove team members");
////        }
////
////        TeamDetails team = teamRepository.findById(teamId)
////                .orElseThrow(() -> new RuntimeException("Team not found"));
////
////        if (!team.getManager().getUserId().equals(managerUserId)) {
////            throw new RuntimeException("You can only remove members from your own teams");
////        }
////
////        List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(teamId);
////        members.stream()
////                .filter(m -> m.getMember().getUserId().equals(memberId))
////                .findFirst()
////                .ifPresent(teamMembersRepository::delete);
////
////        System.out.println("✅ [Team] Member removed: " + memberId);
////    }
////
////    // ==================== DELETE TEAM ====================
////
////    @Transactional
////    public void deleteTeam(Long managerUserId, Long teamId) {
////        System.out.println("🔍 [Team] deleteTeam - managerId: " + managerUserId + ", teamId: " + teamId);
////
////        if (!isManager(managerUserId)) {
////            throw new RuntimeException("Only managers can delete teams");
////        }
////
////        TeamDetails team = teamRepository.findById(teamId)
////                .orElseThrow(() -> new RuntimeException("Team not found"));
////
////        if (!team.getManager().getUserId().equals(managerUserId)) {
////            throw new RuntimeException("You can only delete your own teams");
////        }
////
////        teamMembersRepository.deleteByTeamTeamId(teamId);
////        teamRepository.delete(team);
////        System.out.println("✅ [Team] Team deleted: " + teamId);
////    }
////
////    // ==================== GET PROJECT TEAMS ====================
////
////    public List<TeamResponseDTO> getProjectTeams(Long userId, Long projectId) {
////        System.out.println("🔍 [Team] getProjectTeams - userId: " + userId + ", projectId: " + projectId);
////
////        ProjectDetails project = projectRepository.findById(projectId)
////                .orElseThrow(() -> new RuntimeException("Project not found"));
////
////        boolean hasAccess = false;
////
////        if (project.getUploader().getUserId().equals(userId)) {
////            hasAccess = true;
////        }
////
////        if (project.getAssignedManager() != null && project.getAssignedManager().getUserId().equals(userId)) {
////            hasAccess = true;
////        }
////
////        if (isDirector(userId)) {
////            Long directorDeptId = getManagerDepartmentId(userId);
////            if (project.getDepartment() != null && project.getDepartment().getDepartmentId().equals(directorDeptId)) {
////                hasAccess = true;
////            }
////        }
////
////        List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(userId);
////        boolean isTeamMember = userTeams.stream()
////                .anyMatch(tm -> tm.getTeam().getProject().getProjectId().equals(projectId));
////        if (isTeamMember) {
////            hasAccess = true;
////        }
////
////        if (!hasAccess) {
////            throw new RuntimeException("You don't have access to view this project's teams");
////        }
////
////        return teamRepository.findByProjectProjectId(projectId)
////                .stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    // ==================== GET MY TEAMS ====================
////
////    public List<TeamResponseDTO> getMyTeams(Long managerUserId) {
////        System.out.println("🔍 [Team] getMyTeams - managerId: " + managerUserId);
////
////        if (!isManager(managerUserId)) {
////            throw new RuntimeException("Only managers can view their teams");
////        }
////
////        List<TeamDetails> teams = teamRepository.findByManagerUserId(managerUserId);
////        System.out.println("✅ [Team] Found " + teams.size() + " teams");
////
////        return teams.stream()
////                .map(this::convertToDTO)
////                .collect(Collectors.toList());
////    }
////
////    // ==================== GET MY CONSULTANT TEAMS ====================
////
////    public List<TeamResponseDTO> getMyConsultantTeams(Long consultantUserId) {
////        System.out.println("🔍 [Team] getMyConsultantTeams - consultantId: " + consultantUserId);
////
////        List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(consultantUserId);
////        System.out.println("✅ [Team] Found " + userTeams.size() + " teams for consultant");
////
////        return userTeams.stream()
////                .map(tm -> convertToDTO(tm.getTeam()))
////                .collect(Collectors.toList());
////    }
////
////    // ==================== CONVERT TO DTO ====================
////
////    private TeamResponseDTO convertToDTO(TeamDetails team) {
////        List<TeamMembers> members = teamMembersRepository.findByTeamTeamId(team.getTeamId());
////        List<TeamMemberDTO> memberDTOs = members.stream()
////                .map(m -> TeamMemberDTO.builder()
////                        .memberId(m.getMember().getUserId())
////                        .memberName(m.getMember().getName())
////                        .memberEmail(m.getMember().getEmail())
////                        .photoUrl(m.getMember().getPhotoUrl())
////                        .role(m.getRole())
////                        .description(m.getDescription())
////                        .build())
////                .collect(Collectors.toList());
////
////        return TeamResponseDTO.builder()
////                .teamId(team.getTeamId())
////                .teamName(team.getTeamName())
////                .projectId(team.getProject().getProjectId())
////                .projectName(team.getProject().getProjectName())
////                .managerId(team.getManager().getUserId())
////                .managerName(team.getManager().getName())
////                .members(memberDTOs)
////                .createdAt(team.getCreatedAt())
////                .build();
////    }
////
////    // ==================== EMAIL METHODS ====================
////
////    private void sendTeamCreatedEmail(User manager, TeamDetails team, ProjectDetails project) {
////        try {
////            String subject = "🎉 Team Created Successfully - " + team.getTeamName();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: #4CAF50;'>🎉 Team Created Successfully!</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>Your team <strong>'%s'</strong> has been created successfully for project <strong>'%s'</strong>.</p>
////                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>📋 Team Details:</strong></p>
////                            <p>• Team ID: %d</p>
////                            <p>• Team Name: %s</p>
////                            <p>• Project: %s</p>
////                        </div>
////                        <p>You can now add more members to your team from the dashboard.</p>
////                        <hr style='margin: 20px 0;'>
////                        <p style='color: #777; font-size: 12px;'>Team Gaurion Bharat</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    manager.getName(),
////                    team.getTeamName(),
////                    project.getProjectName(),
////                    team.getTeamId(),
////                    team.getTeamName(),
////                    project.getProjectName()
////            );
////            emailService.sendEmail(manager.getEmail(), subject, html);
////            System.out.println("✅ [Team] Team creation email sent to manager: " + manager.getEmail());
////        } catch (Exception e) {
////            System.out.println("❌ [Team] Email error: " + e.getMessage());
////        }
////    }
////
////    private void sendTeamAssignmentEmail(User member, TeamDetails team, User manager) {
////        try {
////            String subject = "👋 You've been added to a team - " + team.getTeamName();
////            String html = String.format("""
////                <html>
////                <body style='font-family: Arial, sans-serif; padding: 20px;'>
////                    <div style='max-width: 600px; margin: 0 auto; background: #f9f9f9; border-radius: 10px; padding: 20px;'>
////                        <h2 style='color: #2196F3;'>👋 Welcome to the Team!</h2>
////                        <p>Dear <strong>%s</strong>,</p>
////                        <p>You have been added to team <strong>'%s'</strong> by <strong>%s</strong>.</p>
////                        <div style='background: #fff; padding: 15px; border-radius: 8px; margin: 15px 0;'>
////                            <p><strong>📋 Team Details:</strong></p>
////                            <p>• Team Name: %s</p>
////                            <p>• Project: %s</p>
////                            <p>• Team Manager: %s</p>
////                        </div>
////                        <p>You can now view and work on tasks assigned to this team.</p>
////                        <hr style='margin: 20px 0;'>
////                        <p style='color: #777; font-size: 12px;'>Team Gaurion Bharat</p>
////                    </div>
////                </body>
////                </html>
////                """,
////                    member.getName(),
////                    team.getTeamName(),
////                    manager.getName(),
////                    team.getTeamName(),
////                    team.getProject().getProjectName(),
////                    manager.getName()
////            );
////            emailService.sendEmail(member.getEmail(), subject, html);
////            System.out.println("✅ [Team] Team assignment email sent to: " + member.getEmail());
////        } catch (Exception e) {
////            System.out.println("❌ [Team] Email error: " + e.getMessage());
////        }
////    }
////}