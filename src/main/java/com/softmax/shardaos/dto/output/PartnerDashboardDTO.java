package com.softmax.shardaos.dto.output;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class PartnerDashboardDTO {

    private Long partnerId;
    private String partnerCode;
    private String companyName;
    private String partnerStatus;

    // Clients
    private long totalClients;
    private long approvedClients;
    private long pendingClients;

    // Projects
    private long totalProjects;
    private long activeProjects;
    private long completedProjects;

    // Payments
    private long totalPaymentsCount;
    private double totalApprovedRevenue;
    private double totalPendingRevenue;

    // Recent
    private List<PartnerClientResponseDTO> recentClients;
}