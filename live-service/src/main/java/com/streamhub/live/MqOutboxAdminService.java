package com.streamhub.live;

import com.streamhub.common.api.BusinessException;
import com.streamhub.common.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MqOutboxAdminService {
    private final MqOutboxRepository outboxRepository;
    private final OpsAuditRepository auditRepository;

    public MqOutboxAdminService(MqOutboxRepository outboxRepository, OpsAuditRepository auditRepository) {
        this.outboxRepository = outboxRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public MqOutboxRepository.OutboxEntry retry(long id, long operatorId, String reason) {
        outboxRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Outbox 事件不存在"));
        if (!outboxRepository.retryFailed(id)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "只有 FAILED 状态的 Outbox 事件可以重试");
        }
        auditRepository.append(operatorId, "RETRY_MQ_OUTBOX", "MQ_OUTBOX", String.valueOf(id), reason);
        return outboxRepository.findByIdForUpdate(id).orElseThrow();
    }
}
