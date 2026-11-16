package com.softmax.shardaos.service;

import com.softmax.shardaos.dto.output.FileAccessTokenDTO;
import com.softmax.shardaos.dto.output.FileResponseDTO;
import com.softmax.shardaos.dto.output.FileViewResponseDTO;
import com.softmax.shardaos.models.*;
import com.softmax.shardaos.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class FileManagementService {

    @Autowired
    private ProjectFilesRepository projectFilesRepository;

    @Autowired
    private ProjectDetailsRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTypeRepository userTypeRepository;

    @Autowired
    private TeamMembersRepository teamMembersRepository;

    @Autowired
    private EmployeeDetailsRepository employeeDetailsRepository;

    @Value("${file.upload.directory:uploads/projects/}")
    private String uploadDirectory;

    // In-memory token store (in production, use Redis or Database)
    private final Map<String, FileTokenData> tokenStore = new ConcurrentHashMap<>();

    private static final long TOKEN_EXPIRY_MINUTES = 15;

    // ==================== HELPER METHODS ====================

    private boolean isManager(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
                .filter(ut -> "MANAGER".equals(ut.getStype()))
                .isPresent();
    }

    private boolean isDirector(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "EMP")
                .filter(ut -> "DIRECTOR".equals(ut.getStype()))
                .isPresent();
    }

    private boolean isClient(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "CLIENT").isPresent();
    }

    private boolean isConsultant(Long userId) {
        return userTypeRepository.findByUserUserIdAndPtype(userId, "CONSULTANT").isPresent();
    }

    private boolean isHeadDirector(Long userId) {
        if (!isDirector(userId)) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 1L;
    }

    private boolean isHRDirector(Long userId) {
        if (!isDirector(userId)) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 2L;
    }

    private boolean isBDEDirector(Long userId) {
        if (!isDirector(userId)) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 4L;
    }

    private boolean isFinancialDirector(Long userId) {
        if (!isDirector(userId)) return false;
        EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
        return emp != null && emp.getDepartmentRef() != null && emp.getDepartmentRef().getDepartmentId() == 3L;
    }

    // Check if user has access to file
    private boolean hasFileAccess(Long userId, ProjectFiles file) {
        ProjectDetails project = file.getProject();

        // Uploader always has access
        if (file.getUploader().getUserId().equals(userId)) return true;

        // Client access
        if (isClient(userId) && project.getUploader().getUserId().equals(userId)) return true;

        // Manager access (assigned to project)
        if (isManager(userId) && project.getAssignedManager() != null
                && project.getAssignedManager().getUserId().equals(userId)) return true;

        // Director access (Head, HR, BDE, Financial, Core)
        if (isDirector(userId)) {
            // Head Director sees everything
            if (isHeadDirector(userId)) return true;

            // HR Director sees all files
            if (isHRDirector(userId)) return true;

            // BDE Director sees BD department files
            if (isBDEDirector(userId) && project.getDepartment() != null
                    && project.getDepartment().getDepartmentId() == 4L) return true;

            // Financial Director sees all files
            if (isFinancialDirector(userId)) return true;

            // Core Director sees own department files
            EmployeeDetails emp = employeeDetailsRepository.findByUserUserId(userId).orElse(null);
            if (emp != null && emp.getDepartmentRef() != null && project.getDepartment() != null
                    && emp.getDepartmentRef().getDepartmentId().equals(project.getDepartment().getDepartmentId())) {
                return true;
            }
        }

        // Consultant access (team member)
        if (isConsultant(userId)) {
            List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(userId);
            boolean isTeamMember = userTeams.stream()
                    .anyMatch(tm -> tm.getTeam().getProject().getProjectId().equals(project.getProjectId()));
            if (isTeamMember) return true;
        }

        return false;
    }

    // ==================== GENERATE SECURE TOKEN ====================

    public FileAccessTokenDTO generateSecureAccessToken(Long userId, Long fileId) {
        ProjectFiles file = projectFilesRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!hasFileAccess(userId, file)) {
            throw new RuntimeException("Access denied to this file");
        }

        String token = UUID.randomUUID().toString() + System.currentTimeMillis();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES);

        tokenStore.put(token, new FileTokenData(file, expiresAt, userId));

        // Cleanup expired tokens
        cleanupExpiredTokens();

        return FileAccessTokenDTO.builder()
                .accessToken(token)
                .secureUrl("/api/file/view/" + token)
                .expiresAt(expiresAt)
                .build();
    }

    public Resource getFileByToken(String token) {
        FileTokenData tokenData = tokenStore.get(token);

        if (tokenData == null) {
            throw new RuntimeException("Invalid or expired token");
        }

        if (tokenData.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenStore.remove(token);
            throw new RuntimeException("Token has expired");
        }

        Path filePath = Paths.get(tokenData.getFile().getFileLink());
        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found on server");
        }

        return new FileSystemResource(filePath.toFile());
    }

    public String getContentType(String token) {
        FileTokenData tokenData = tokenStore.get(token);
        if (tokenData == null) throw new RuntimeException("Invalid token");

        String fileName = Paths.get(tokenData.getFile().getFileLink()).getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        switch (extension) {
            case "pdf": return "application/pdf";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "mp4": return "video/mp4";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default: return "application/octet-stream";
        }
    }

    public FileViewResponseDTO getFileInfoByToken(String token) {
        FileTokenData tokenData = tokenStore.get(token);
        if (tokenData == null) throw new RuntimeException("Invalid token");

        ProjectFiles file = tokenData.getFile();
        Path filePath = Paths.get(file.getFileLink());
        String fileName = filePath.getFileName().toString();
        long fileSize = 0;

        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            fileSize = 0;
        }

        return FileViewResponseDTO.builder()
                .embedUrl("/api/file/view/" + token)
                .downloadUrl("/api/file/download/" + token)
                .fileType(getContentType(token))
                .fileName(fileName)
                .fileSize(fileSize)
                .build();
    }

    private void cleanupExpiredTokens() {
        tokenStore.entrySet().removeIf(entry -> entry.getValue().getExpiresAt().isBefore(LocalDateTime.now()));
    }

    // ==================== FILE UPLOAD ====================

    @Transactional
    public FileResponseDTO uploadFile(Long userId, Long projectId, String fileType, MultipartFile file) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        boolean hasAccess = checkUploadAccess(userId, project);
        if (!hasAccess) {
            throw new RuntimeException("You don't have permission to upload files to this project");
        }

        User uploader = userRepository.findById(userId).get();

        try {
            Path uploadPath = Paths.get(uploadDirectory + "project_" + projectId);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());

            ProjectFiles projectFile = new ProjectFiles();
            projectFile.setFileType(fileType);
            projectFile.setFileLink(filePath.toString());
            projectFile.setUploader(uploader);
            projectFile.setUploadTime(LocalDateTime.now());
            projectFile.setDepartment(project.getDepartment());
            projectFile.setProject(project);
            projectFile = projectFilesRepository.save(projectFile);

            return convertToDTO(projectFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage());
        }
    }

    private boolean checkUploadAccess(Long userId, ProjectDetails project) {
        if (isClient(userId) && project.getUploader().getUserId().equals(userId)) return true;
        if (isManager(userId) && project.getAssignedManager() != null
                && project.getAssignedManager().getUserId().equals(userId)) return true;
        if (isDirector(userId)) return true;
        if (isConsultant(userId)) {
            List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(userId);
            return userTeams.stream()
                    .anyMatch(tm -> tm.getTeam().getProject().getProjectId().equals(project.getProjectId()));
        }
        return false;
    }

    // ==================== GET FILES ====================

    public List<FileResponseDTO> getProjectFiles(Long userId, Long projectId) {
        ProjectDetails project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        boolean hasAccess = checkViewAccess(userId, project);
        if (!hasAccess) {
            throw new RuntimeException("You don't have access to view files of this project");
        }

        return projectFilesRepository.findByProject_ProjectId(projectId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private boolean checkViewAccess(Long userId, ProjectDetails project) {
        if (isClient(userId) && project.getUploader().getUserId().equals(userId)) return true;
        if (isManager(userId) && project.getAssignedManager() != null
                && project.getAssignedManager().getUserId().equals(userId)) return true;
        if (isDirector(userId)) return true;
        if (isConsultant(userId)) {
            List<TeamMembers> userTeams = teamMembersRepository.findByMemberUserId(userId);
            return userTeams.stream()
                    .anyMatch(tm -> tm.getTeam().getProject().getProjectId().equals(project.getProjectId()));
        }
        return false;
    }

    public List<FileResponseDTO> getMyUploads(Long userId) {
        return projectFilesRepository.findByUploaderUserId(userId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<FileResponseDTO> getDepartmentFiles(Long directorUserId) {
        if (!isDirector(directorUserId)) {
            throw new RuntimeException("Only directors can view department files");
        }

        EmployeeDetails employee = employeeDetailsRepository.findByUserUserId(directorUserId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (employee.getDepartmentRef() == null) {
            throw new RuntimeException("Director not assigned to any department");
        }

        return projectFilesRepository.findByDepartmentDepartmentId(employee.getDepartmentRef().getDepartmentId())
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ==================== DELETE FILE ====================

    @Transactional
    public void deleteFile(Long userId, Long fileId) {
        ProjectFiles file = projectFilesRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!file.getUploader().getUserId().equals(userId) && !isDirector(userId)) {
            throw new RuntimeException("You can only delete your own files");
        }

        try {
            Path filePath = Paths.get(file.getFileLink());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.out.println("Failed to delete physical file: " + e.getMessage());
        }

        projectFilesRepository.delete(file);
    }

    // ==================== HELPER ====================

    private FileResponseDTO convertToDTO(ProjectFiles file) {
        return FileResponseDTO.builder()
                .fileId(file.getFileId())
                .fileType(file.getFileType())
                .fileLink(file.getFileLink())
                .uploaderId(file.getUploader().getUserId())
                .uploaderName(file.getUploader().getName())
                .uploadTime(file.getUploadTime())
                .departmentId(file.getDepartment() != null ? file.getDepartment().getDepartmentId() : null)
                .departmentName(file.getDepartment() != null ? file.getDepartment().getDepartmentName() : null)
                .build();
    }

    // Inner class for token data
    private static class FileTokenData {
        private final ProjectFiles file;
        private final LocalDateTime expiresAt;
        private final Long userId;

        public FileTokenData(ProjectFiles file, LocalDateTime expiresAt, Long userId) {
            this.file = file;
            this.expiresAt = expiresAt;
            this.userId = userId;
        }

        public ProjectFiles getFile() { return file; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public Long getUserId() { return userId; }
    }
}