package com.myagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myagent.mapper.PromptTemplateMapper;
import com.myagent.model.PromptTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(value = "prompt_templates", key = "'all'")
    public List<PromptTemplate> getAll() {
        return mapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>()
                        .orderByAsc(PromptTemplate::getSortOrder)
                        .orderByDesc(PromptTemplate::getUpdatedAt));
    }

    @Transactional
    @CacheEvict(value = "prompt_templates", key = "'all'")
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
    @CacheEvict(value = "prompt_templates", key = "'all'")
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
    @CacheEvict(value = "prompt_templates", key = "'all'")
    public void delete(String id) {
        if (mapper.selectById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found: " + id);
        }
        mapper.deleteById(id);
    }
}
