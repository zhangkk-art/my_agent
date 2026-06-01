package com.myagent.controller;

import com.myagent.model.WorkflowTemplate;
import com.myagent.service.WorkflowTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow-templates")
public class WorkflowTemplateController {

    private final WorkflowTemplateService service;

    public WorkflowTemplateController(WorkflowTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkflowTemplate> getAll() {
        return service.getAll();
    }

    @PostMapping
    public WorkflowTemplate create(@RequestBody Map<String, String> body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public WorkflowTemplate update(@PathVariable String id, @RequestBody Map<String, String> body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
