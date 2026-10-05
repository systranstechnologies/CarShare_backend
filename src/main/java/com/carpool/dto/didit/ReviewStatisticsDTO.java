package com.carpool.dto.didit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStatisticsDTO {
    private Long pendingReviews;
    private Long approvedReviews;
    private Long declinedReviews;
    private Long resubmissionRequests;
}
