package com.myagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myagent.mapper.WorkflowTemplateMapper;
import com.myagent.model.WorkflowTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowTemplateService {

    private final WorkflowTemplateMapper mapper;

    public WorkflowTemplateService(WorkflowTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Cacheable(value = "workflow_templates", key = "'all'")
    public List<WorkflowTemplate> getAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<WorkflowTemplate>()
                        .orderByAsc(WorkflowTemplate::getSortOrder)
                        .orderByDesc(WorkflowTemplate::getUpdatedAt));
    }

    @Transactional
    @CacheEvict(value = "workflow_templates", key = "'all'")
    public WorkflowTemplate create(Map<String, String> body) {
        WorkflowTemplate t = new WorkflowTemplate();
        t.setId(UUID.randomUUID().toString());
        t.setName(body.getOrDefault("name", "未命名模板"));
        t.setDescription(body.get("description"));
        t.setSystemPrompt(body.get("systemPrompt"));
        t.setInitialMessage(body.get("initialMessage"));
        t.setSortOrder(0);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        mapper.insert(t);
        return t;
    }

    @Transactional
    @CacheEvict(value = "workflow_templates", key = "'all'")
    public WorkflowTemplate update(String id, Map<String, String> body) {
        WorkflowTemplate t = mapper.selectById(id);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow template not found: " + id);
        }
        if (body.containsKey("name"))          t.setName(body.get("name"));
        if (body.containsKey("description"))   t.setDescription(body.get("description"));
        if (body.containsKey("systemPrompt"))  t.setSystemPrompt(body.get("systemPrompt"));
        if (body.containsKey("initialMessage")) t.setInitialMessage(body.get("initialMessage"));
        t.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(t);
        return t;
    }

    @Transactional
    @CacheEvict(value = "workflow_templates", key = "'all'")
    public void delete(String id) {
        if (mapper.selectById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow template not found: " + id);
        }
        mapper.deleteById(id);
    }
}
