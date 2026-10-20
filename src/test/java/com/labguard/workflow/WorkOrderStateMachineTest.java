package com.labguard.workflow;

import com.labguard.model.WorkOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工单状态机测试。遍历 3×3 全部状态组合：
 * 4 条合法路径必须通过，5 条非法路径必须拒绝。
 */
class WorkOrderStateMachineTest {

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    @DisplayName("待整改：只有提交整改（→待复查）是合法的")
    void pendingRemediationOnlyAllowsSubmit(WorkOrderStatus target) {
        boolean expected = target == WorkOrderStatus.PENDING_REVIEW;
        assertEquals(expected,
                WorkOrderStateMachine.canTransition(WorkOrderStatus.PENDING_REMEDIATION, target),
                "待整改 → " + target);
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    @DisplayName("待复查：可以复查通过（→已关闭）或退回（→待整改）")
    void pendingReviewAllowsPassOrReturn(WorkOrderStatus target) {
        boolean expected = target == WorkOrderStatus.CLOSED
                || target == WorkOrderStatus.PENDING_REMEDIATION;
        assertEquals(expected,
                WorkOrderStateMachine.canTransition(WorkOrderStatus.PENDING_REVIEW, target),
                "待复查 → " + target);
    }

    @ParameterizedTest
    @EnumSource(WorkOrderStatus.class)
    @DisplayName("已关闭：任何转换都不允许")
    void closedAllowsNothing(WorkOrderStatus target) {
        assertFalse(WorkOrderStateMachine.canTransition(WorkOrderStatus.CLOSED, target),
                "已关闭 → " + target);
    }

    @Test
    @DisplayName("合法转换不抛异常")
    void legalTransitionsDoNotThrow() {
        assertDoesNotThrow(() -> WorkOrderStateMachine.assertTransition(
                WorkOrderStatus.PENDING_REMEDIATION, WorkOrderStatus.PENDING_REVIEW));
        assertDoesNotThrow(() -> WorkOrderStateMachine.assertTransition(
                WorkOrderStatus.PENDING_REVIEW, WorkOrderStatus.CLOSED));
        assertDoesNotThrow(() -> WorkOrderStateMachine.assertTransition(
                WorkOrderStatus.PENDING_REVIEW, WorkOrderStatus.PENDING_REMEDIATION));
    }

    @Test
    @DisplayName("非法转换抛 WorkflowException，文案含当前状态")
    void illegalTransitionThrowsWithReadableMessage() {
        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> WorkOrderStateMachine.assertTransition(
                        WorkOrderStatus.CLOSED, WorkOrderStatus.PENDING_REMEDIATION));
        assertEquals(WorkflowException.Code.ILLEGAL_TRANSITION, ex.code());
        assertTrue(ex.getMessage().contains("已关闭"), "文案应包含当前状态：" + ex.getMessage());
    }

    @Test
    @DisplayName("空状态按非法处理，不抛空指针")
    void nullStatusIsIllegal() {
        assertFalse(WorkOrderStateMachine.canTransition(null, WorkOrderStatus.PENDING_REVIEW));
        assertFalse(WorkOrderStateMachine.canTransition(WorkOrderStatus.PENDING_REVIEW, null));
    }

    @Test
    @DisplayName("合法转换集合与文档一致：共 4 条")
    void allowedTransitionCount() {
        int count = WorkOrderStateMachine.allowedTransitions()
                .values().stream().mapToInt(Set::size).sum();
        assertEquals(4, count);
    }

    @Test
    @DisplayName("合法转换集合不可被外部修改")
    void allowedTransitionsIsReadOnly() {
        var transitions = WorkOrderStateMachine.allowedTransitions();
        assertThrows(UnsupportedOperationException.class,
                () -> transitions.get(WorkOrderStatus.CLOSED).add(WorkOrderStatus.PENDING_REVIEW));
        assertFalse(WorkOrderStateMachine.canTransition(
                WorkOrderStatus.CLOSED, WorkOrderStatus.PENDING_REVIEW));
    }
}
