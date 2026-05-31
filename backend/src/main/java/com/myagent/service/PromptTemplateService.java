package com.myagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myagent.mapper.PromptTemplateMapper;
import com.myagent.model.PromptTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PromptTemplateService {

    private final PromptTemplateMapper mapper;

    public PromptTemplateService(PromptTemplateMapper mapper) {
        this.mapper = mapper;
    }

    public List<PromptTemplate> getAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>()
                        .orderByAsc(PromptTemplate::getSortOrder)
                        .orderByDesc(PromptTemplate::getUpdatedAt));
    }

    @Transactional
    public PromptTemplate create(String name, String content) {
        PromptTemplate t = new PromptTemplate();
        t.setId(UUID.randomUUID().toString());
        t.setName(name);
        t.setContent(content);
        t.setSortOrder(0);
        t.setCreatedAt(LocalDateTime.now());
        t.setUpdatedAt(LocalDateTime.now());
        mapper.insert(t);
        return t;
    }

    @Transactional
    public PromptTemplate update(String id, String name, String content) {
        PromptTemplate t = mapper.selectById(id);
        if (t == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found: " + id);
        }
        t.setName(name);
        t.setContent(content);
        t.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(t);
        return t;
    }

    @Transactional
    public void delete(String id) {
        if (mapper.selectById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found: " + id);
        }
        mapper.deleteById(id);
    }
}
