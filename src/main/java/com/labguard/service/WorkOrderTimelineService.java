package com.labguard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labguard.model.AuditEvent;
import com.labguard.repository.AuditEventRepository;
import com.labguard.web.view.TimelineEntry;
import com.labguard.workflow.WorkflowAction;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工单处理时间线。工单本身只保留最新一次整改，退回后再次提交的历史从 audit_event 还原。
 */
@Service
public class WorkOrderTimelineService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkOrderTimelineService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public List<TimelineEntry> entriesFor(long workOrderId) {
        return auditEventRepository.findByEntity(AuditEvent.ENTITY_WORK_ORDER, workOrderId).stream()
                .map(event -> TimelineEntry.of(event, remediationNote(event)))
                .toList();
    }

    /** 整改事件把当次整改说明存在 detail 里，时间线优先展示这段内容。 */
    private String remediationNote(AuditEvent event) {
        if (!WorkflowAction.SUBMIT_REMEDIATION.name().equals(event.action()) || event.detail() == null) {
            return null;
        }
        try {
            JsonNode note = objectMapper.readTree(event.detail()).get("remediationNote");
            return note == null ? null : note.asText();
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
