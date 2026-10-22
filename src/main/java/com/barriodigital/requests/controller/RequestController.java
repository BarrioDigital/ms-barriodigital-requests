package com.barriodigital.requests.controller;

import com.barriodigital.requests.model.RequestEntity;
import com.barriodigital.requests.model.RequestStatus;
import com.barriodigital.requests.service.RequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    @Autowired
    private RequestService service;

    @GetMapping
    public ResponseEntity<List<RequestEntity>> getAll() {
        return ResponseEntity.ok(service.getAllRequests());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RequestEntity> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<RequestEntity> createRequest(@RequestBody RequestEntity request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRequest(request));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<RequestEntity> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        
        RequestStatus status = RequestStatus.valueOf(body.get("status"));
        String crew = body.get("crew");
        
        return ResponseEntity.ok(service.updateStatus(id, status, crew));
    }
}