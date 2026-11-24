package com.tom.payment.routinemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String reply;
    /** A goal Buddy proposes in this reply, or null (F013). */
    private GoalProposalView goalProposal;
}
