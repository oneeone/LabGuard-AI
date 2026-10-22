package com.labguard.service;

import com.labguard.model.AiResultSource;
import com.labguard.model.AiReview;
import com.labguard.model.AnalysisStatus;
import com.labguard.model.CheckStatus;
import com.labguard.model.Comparability;
import com.labguard.model.EvidenceCheck;
import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.Inspection;
import com.labguard.model.RemediationReview;
import com.labguard.model.ReviewOutcome;
import com.labguard.model.RiskLevel;
import com.labguard.model.RiskLevelSource;
import com.labguard.model.WorkOrder;
import com.labguard.model.WorkOrderDetail;
import com.labguard.model.WorkOrderStatus;
import com.labguard.repository.AiReviewRepository;
import com.labguard.workflow.WorkflowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 编排层：AI 核验只落库、不改状态；非待复查状态与缺图直接拒绝。
 */
class RemediationReviewServiceTest {

    private static final long ORDER_ID = 12L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 20, 0);

    private final WorkOrderService orderService = mock(WorkOrderService.class);
    private final LocalFileStorage fileStorage = mock(LocalFileStorage.class);
    private final AiReviewRepository repository = mock(AiReviewRepository.class);
    private final RemediationReviewService service = new RemediationReviewService(
            orderService, fileStorage, new DemoAiVisionService(), repository);

    @Test
    @DisplayName("A-22-02 待复查工单执行核验：双图读入、逐点结果落库、状态不变")
    void reviewPersistsWithoutTouchingStatus() {
        given(orderService.detail(ORDER_ID)).willReturn(detail(WorkOrderStatus.PENDING_REVIEW, "/uploads/after.png"));
        given(fileStorage.load("/uploads/before.png")).willReturn("before".getBytes());
        given(fileStorage.load("/uploads/after.png")).willReturn("after".getBytes());
        given(repository.insert(any(AiReview.class))).willReturn(88L);

        AiReview review = service.review(ORDER_ID);

        assertThat(review.id()).isEqualTo(88L);
        assertThat(review.workOrderId()).isEqualTo(ORDER_ID);
        assertThat(review.outcome().name()).isEqualTo("INCONCLUSIVE");
        assertThat(review.source()).isEqualTo(AiResultSource.DEMO);
        assertThat(review.checks()).isNotEmpty();
        assertThat(review.needsHumanReview()).isTrue();

        ArgumentCaptor<AiReview> captor = ArgumentCaptor.forClass(AiReview.class);
        verify(repository).insert(captor.capture());
        assertThat(captor.getValue().checks()).isNotEmpty();
        // 编排服务不调用任何工单状态更新方法。
        verify(orderService, never()).review(anyLong(), anyBoolean(), any(), anyString());
    }

    @Test
    @DisplayName("A-22-03 非待复查状态直接拒绝，不调用 AI 也不落库")
    void reviewRejectedWhenStatusNotPendingReview() {
        given(orderService.detail(ORDER_ID)).willReturn(detail(WorkOrderStatus.PENDING_REMEDIATION, null));

        assertThatThrownBy(() -> service.review(ORDER_ID))
                .isInstanceOfSatisfying(WorkflowException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(WorkflowException.Code.ILLEGAL_TRANSITION);
                    assertThat(ex.getMessage()).contains("待整改");
                });
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("A-22-04 待复查但缺少整改后照片时拒绝")
    void reviewRejectedWithoutAfterImage() {
        given(orderService.detail(ORDER_ID)).willReturn(detail(WorkOrderStatus.PENDING_REVIEW, null));

        assertThatThrownBy(() -> service.review(ORDER_ID))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(WorkflowException.Code.INVALID_ARGUMENT));
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("A-22-05 详情页查询最新核验记录，无记录返回 null")
    void latestForDelegatesToRepository() {
        AiReview latest = new AiReview(9L, ORDER_ID, RemediationReviewFixture.passed().outcome(),
                RemediationReviewFixture.passed().evidence(), RemediationReviewFixture.passed().checks(),
                RemediationReviewFixture.passed().comparability(), "", "", AiResultSource.REAL,
                "m", null, true, NOW);
        given(repository.findLatestByWorkOrderId(ORDER_ID)).willReturn(Optional.of(latest));
        assertThat(service.latestFor(ORDER_ID)).isSameAs(latest);
    }

    private WorkOrderDetail detail(WorkOrderStatus status, String afterImagePath) {
        Hazard hazard = new Hazard(5L, 7L, "通道被遮挡", RiskLevel.MEDIUM, RiskLevelSource.RULE,
                "实验室门口", "纸箱堆放在安全出口前", 0.72, null,
                "移除纸箱；保持通道畅通", false, HazardStatus.CONFIRMED, null, "确认员", NOW, NOW);
        Inspection inspection = new Inspection(7L, "化学实验楼 A302", "张三", NOW,
                "/uploads/before.png", AiResultSource.DEMO, AnalysisStatus.SUCCESS, "{}", null, NOW);
        WorkOrder order = new WorkOrder(ORDER_ID, 5L, "李四", LocalDate.of(2026, 10, 25), status,
                "尽快整改", "已清理纸箱", afterImagePath, null, null, null, NOW, NOW);
        return new WorkOrderDetail(order, hazard, inspection);
    }

    /** 构造一个合法的 PASSED 复查结果，供展示对象使用。 */
    private static final class RemediationReviewFixture {
        private static RemediationReview passed() {
            return new RemediationReview(ReviewOutcome.PASSED, "通道已恢复畅通",
                    List.of(), "", true, "请人工确认", AiResultSource.REAL, "m", Instant.now(),
                    null, List.of(new EvidenceCheck("纸箱堆放在安全出口前",
                            CheckStatus.RESOLVED, "出口前地面空置", "")),
                    Comparability.COMPARABLE);
        }
    }
}
