package com.labguard.workflow;

import com.labguard.model.HazardStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隐患人工确认状态测试：只有待确认可以流转，已确认与已驳回都是终态。
 */
class HazardStateMachineTest {

    @ParameterizedTest
    @EnumSource(HazardStatus.class)
    @DisplayName("待确认：可以确认或驳回")
    void pendingConfirmAllowsConfirmOrReject(HazardStatus target) {
        boolean expected = target == HazardStatus.CONFIRMED || target == HazardStatus.REJECTED;
        assertEquals(expected,
                HazardStateMachine.canTransition(HazardStatus.PENDING_CONFIRM, target),
                "待确认 → " + target);
    }

    @ParameterizedTest
    @EnumSource(HazardStatus.class)
    @DisplayName("已确认是终态")
    void confirmedIsFinal(HazardStatus target) {
        assertFalse(HazardStateMachine.canTransition(HazardStatus.CONFIRMED, target));
    }

    @ParameterizedTest
    @EnumSource(HazardStatus.class)
    @DisplayName("已驳回是终态")
    void rejectedIsFinal(HazardStatus target) {
        assertFalse(HazardStateMachine.canTransition(HazardStatus.REJECTED, target));
    }

    @Test
    @DisplayName("重复确认被拒绝，文案含当前状态")
    void repeatedConfirmIsRejected() {
        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> HazardStateMachine.assertTransition(
                        HazardStatus.CONFIRMED, HazardStatus.CONFIRMED));
        assertEquals(WorkflowException.Code.ILLEGAL_TRANSITION, ex.code());
        assertTrue(ex.getMessage().contains("已确认"));
    }
}
