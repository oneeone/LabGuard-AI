package com.labguard.repository;

import com.labguard.BaseIntegrationTest;
import com.labguard.model.AiResultSource;
import com.labguard.model.AiReview;
import com.labguard.model.CheckStatus;
import com.labguard.model.Comparability;
import com.labguard.model.EvidenceCheck;
import com.labguard.model.ReviewOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ai_review 表读写：逐点核验结果以 JSON 往返，最新记录按 id 倒序取。
 */
class AiReviewRepositoryTest extends BaseIntegrationTest {

    private static final long WORK_ORDER_ID = 990001L;

    @Autowired
    private AiReviewRepository repository;

    @Test
    @DisplayName("A-22-09 核验记录写入后可完整读回，逐点结果不丢字段")
    void insertAndReadBack() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 22, 20, 30);
        AiReview input = new AiReview(0, WORK_ORDER_ID, ReviewOutcome.FAILED, "出口前仍有纸箱",
                List.of(new EvidenceCheck("纸箱堆放在安全出口前", CheckStatus.UNRESOLVED,
                        "出口左侧仍有一个纸箱", "清理不彻底")),
                Comparability.COMPARABLE, "仅依据当前视角", "退回继续整改", AiResultSource.REAL,
                "vision-test-model", "{\"raw\":true}", true, createdAt);

        long id = repository.insert(input);
        AiReview loaded = repository.findLatestByWorkOrderId(WORK_ORDER_ID).orElseThrow();

        assertThat(id).isPositive();
        assertThat(loaded.id()).isEqualTo(id);
        assertThat(loaded.workOrderId()).isEqualTo(WORK_ORDER_ID);
        assertThat(loaded.outcome()).isEqualTo(ReviewOutcome.FAILED);
        assertThat(loaded.evidence()).contains("纸箱");
        assertThat(loaded.checks()).hasSize(1);
        assertThat(loaded.checks().get(0).status()).isEqualTo(CheckStatus.UNRESOLVED);
        assertThat(loaded.checks().get(0).visibleEvidence()).contains("出口左侧");
        assertThat(loaded.comparability()).isEqualTo(Comparability.COMPARABLE);
        assertThat(loaded.source()).isEqualTo(AiResultSource.REAL);
        assertThat(loaded.createdAt()).isEqualTo(createdAt);
        assertThat(loaded.needsHumanReview()).isTrue();
    }

    @Test
    @DisplayName("A-22-10 同一工单多次核验时，详情页取最新一次")
    void latestWins() {
        repository.insert(review(WORK_ORDER_ID, ReviewOutcome.INCONCLUSIVE));
        long latestId = repository.insert(review(WORK_ORDER_ID, ReviewOutcome.PASSED));

        AiReview latest = repository.findLatestByWorkOrderId(WORK_ORDER_ID).orElseThrow();

        assertThat(latest.id()).isEqualTo(latestId);
        assertThat(latest.outcome()).isEqualTo(ReviewOutcome.PASSED);
    }

    private AiReview review(long workOrderId, ReviewOutcome outcome) {
        return new AiReview(0, workOrderId, outcome, "依据",
                List.of(new EvidenceCheck("纸箱堆放在安全出口前",
                        outcome == ReviewOutcome.PASSED ? CheckStatus.RESOLVED : CheckStatus.INCONCLUSIVE,
                        "出口前地面空置", "")),
                Comparability.COMPARABLE, "", "请人工确认", AiResultSource.DEMO, "demo-sample",
                null, true, LocalDateTime.now());
    }
}
