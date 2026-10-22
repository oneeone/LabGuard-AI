package com.labguard.service;

import com.labguard.model.AiReview;
import com.labguard.model.Hazard;
import com.labguard.model.RemediationContext;
import com.labguard.model.RemediationReview;
import com.labguard.model.WorkOrder;
import com.labguard.model.WorkOrderDetail;
import com.labguard.model.WorkOrderStatus;
import com.labguard.repository.AiReviewRepository;
import com.labguard.workflow.WorkflowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 整改照片 AI 核验编排：读取原图与整改后照片 → 组装上下文 → 调用视觉服务 → 落库。
 * 本服务不改变工单状态，只产生可追溯的核验记录，供人工复查时参考。
 * 仅在工单处于「待复查」且已提交整改照片时允许执行。
 */
@Service
public class RemediationReviewService {

    private static final Logger log = LoggerFactory.getLogger(RemediationReviewService.class);

    private final WorkOrderService workOrderService;
    private final LocalFileStorage fileStorage;
    private final AiVisionService aiVisionService;
    private final AiReviewRepository repository;

    public RemediationReviewService(WorkOrderService workOrderService, LocalFileStorage fileStorage,
                                    AiVisionService aiVisionService, AiReviewRepository repository) {
        this.workOrderService = workOrderService;
        this.fileStorage = fileStorage;
        this.aiVisionService = aiVisionService;
        this.repository = repository;
    }

    /**
     * 对指定工单执行一次 AI 前后对比核验并落库。
     * 失败时抛异常，不会写入「通过」记录。
     */
    public AiReview review(long workOrderId) {
        WorkOrderDetail detail = workOrderService.detail(workOrderId);
        WorkOrder order = detail.order();
        if (order.status() != WorkOrderStatus.PENDING_REVIEW) {
            throw new WorkflowException(WorkflowException.Code.ILLEGAL_TRANSITION,
                    "当前状态（" + order.status().label() + "）不允许 AI 核验");
        }
        if (isBlank(order.afterImagePath())) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT,
                    "尚未提交整改照片，不能进行 AI 核验");
        }

        Hazard hazard = detail.hazard();
        ImageInput originalImage = image(detail.inspection().imagePath(), "original");
        ImageInput remediationImage = image(order.afterImagePath(), "remediation");
        RemediationContext context = new RemediationContext(
                "#" + order.id(),
                hazard.category(),
                hazard.location(),
                hazard.suggestedAction(),
                hazard.suggestedAction(),
                order.remediationNote(),
                hazard.evidence(),
                hazard.location());

        RemediationReview review = aiVisionService.reviewRemediation(originalImage, remediationImage, context);
        long id = repository.insert(AiReview.from(workOrderId, review));
        log.info("AI 核验完成：workOrderId={}, outcome={}, comparability={}, source={}",
                workOrderId, review.outcome(), review.comparability(), review.source());
        return AiReview.from(workOrderId, review).withId(id);
    }

    /** 详情页常驻展示用；没有核验记录时返回 null。 */
    public AiReview latestFor(long workOrderId) {
        return repository.findLatestByWorkOrderId(workOrderId).orElse(null);
    }

    private ImageInput image(String path, String fallbackName) {
        if (isBlank(path)) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT,
                    "原始证据照片缺失，无法做前后对照");
        }
        byte[] bytes = fileStorage.load(path);
        String filename = path.substring(path.lastIndexOf('/') + 1);
        if (filename.isBlank()) {
            filename = fallbackName + ".jpg";
        }
        return ImageInput.of(bytes, filename);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
