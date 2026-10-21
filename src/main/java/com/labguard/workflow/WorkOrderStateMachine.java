package com.labguard.workflow;

import com.labguard.model.WorkOrderStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 工单状态转换规则。合法转换集中定义在此，Service 与测试共用同一份规则，
 * 避免规则散落在各处导致前后不一致。
 *
 * <pre>
 * 待整改 → 待复查 → 已关闭
 *              ↘ 待整改（复查退回）
 * </pre>
 */
public final class WorkOrderStateMachine {

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED =
            new EnumMap<>(WorkOrderStatus.class);

    static {
        ALLOWED.put(WorkOrderStatus.PENDING_REMEDIATION, EnumSet.of(WorkOrderStatus.PENDING_REVIEW));
        ALLOWED.put(WorkOrderStatus.PENDING_REVIEW,
                EnumSet.of(WorkOrderStatus.PENDING_REMEDIATION, WorkOrderStatus.CLOSED));
        ALLOWED.put(WorkOrderStatus.CLOSED, EnumSet.noneOf(WorkOrderStatus.class));
    }

    private WorkOrderStateMachine() {
    }

    public static boolean canTransition(WorkOrderStatus from, WorkOrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * 校验状态转换，非法时抛出 {@link WorkflowException}。
     * 文案与 C 模块契约第 6 节保持一致。
     */
    public static void assertTransition(WorkOrderStatus from, WorkOrderStatus to) {
        if (!canTransition(from, to)) {
            throw new WorkflowException(WorkflowException.Code.ILLEGAL_TRANSITION,
                    "当前状态（%s）不允许该操作".formatted(from == null ? "未知" : from.label()));
        }
    }

    /** 全部合法转换的只读副本，供测试遍历使用。 */
    public static Map<WorkOrderStatus, Set<WorkOrderStatus>> allowedTransitions() {
        Map<WorkOrderStatus, Set<WorkOrderStatus>> copy = new EnumMap<>(WorkOrderStatus.class);
        ALLOWED.forEach((from, targets) -> copy.put(from, Set.copyOf(targets)));
        return copy;
    }
}
