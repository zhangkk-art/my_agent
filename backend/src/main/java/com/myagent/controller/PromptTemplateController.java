package com.myagent.controller;

import com.myagent.model.PromptTemplate;
import com.myagent.service.PromptTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prompt-templates")
public class PromptTemplateController {

    private final PromptTemplateService service;

    public PromptTemplateController(PromptTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<PromptTemplate> getAll() {
        return service.getAll();
    }

    @PostMapping
    public PromptTemplate create(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "Untitled");
        String content = body.getOrDefault("content", "");
        return service.create(name, content);
    }

    @PutMapping("/{id}")
    public PromptTemplate update(@PathVariable String id, @RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "Untitled");
        String content = body.getOrDefault("content", "");
        return service.update(id, name, content);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
