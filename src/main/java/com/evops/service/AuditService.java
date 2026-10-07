package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evops.entity.AuditEvent;
import com.evops.mapper.AuditEventMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审计事件：航线规划与禁飞区的关键动作统一留痕。
 * 与业务写入同事务，保证业务落库则留痕必在。
 */
@Service
public class AuditService extends ServiceImpl<AuditEventMapper, AuditEvent> {

    public void record(String entityType, Long entityId, String action, String detail, String operator) {
        AuditEvent event = new AuditEvent();
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setAction(action);
        event.setDetail(detail);
        event.setOperator(operator);
        save(event);
    }

    /** 按对象追溯全部动作 */
    public List<AuditEvent> listByEntity(String entityType, Long entityId) {
        return list(new QueryWrapper<AuditEvent>()
                .eq("entity_type", entityType)
                .eq("entity_id", entityId)
                .orderByAsc("id"));
    }
}
