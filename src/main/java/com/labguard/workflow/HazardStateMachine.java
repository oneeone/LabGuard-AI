package com.labguard.workflow;

import com.labguard.model.HazardStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 隐患人工确认状态规则。
 *
 * <pre>
 * 待确认 → 已确认（CONFIRM / EDIT 均到此状态）
 *        ↘ 已驳回（REJECT）
 * </pre>
 *
 * 已确认、已驳回都是终态，不允许再次变更。
 */
public final class HazardStateMachine {

    private static final Map<HazardStatus, Set<HazardStatus>> ALLOWED =
            new EnumMap<>(HazardStatus.class);

    static {
        ALLOWED.put(HazardStatus.PENDING_CONFIRM,
                EnumSet.of(HazardStatus.CONFIRMED, HazardStatus.REJECTED));
        ALLOWED.put(HazardStatus.CONFIRMED, EnumSet.noneOf(HazardStatus.class));
        ALLOWED.put(HazardStatus.REJECTED, EnumSet.noneOf(HazardStatus.class));
    }

    private HazardStateMachine() {
    }

    public static boolean canTransition(HazardStatus from, HazardStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertTransition(HazardStatus from, HazardStatus to) {
        if (!canTransition(from, to)) {
            throw new WorkflowException(WorkflowException.Code.ILLEGAL_TRANSITION,
                    "当前状态（%s）不允许该操作".formatted(from == null ? "未知" : from.label()));
        }
    }
}
