package com.barriodigital.requests.controller;

import com.barriodigital.requests.entity.Request;
import com.barriodigital.requests.model.RequestStatus;
import com.barriodigital.requests.service.RequestService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    // GET /api/requests -> Listar solicitudes
    @GetMapping
    public ResponseEntity<List<Request>> getAllRequests() {
        return ResponseEntity.ok(requestService.findAll());
    }

    // GET /api/requests/{id} -> Obtener solicitud por ID
    @GetMapping("/{id}")
    public ResponseEntity<Request> getRequestById(@PathVariable Long id) {
        return requestService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/requests -> Crear solicitud
    @PostMapping
    public ResponseEntity<Request> createRequest(@RequestBody Request request) {
        Request created = requestService.createRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // PUT /api/requests/{id}/status -> Cambiar estado
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest body) {
        try {
            return requestService.updateStatus(id, body.getStatus(), body.getCrew())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @Data
    public static class StatusUpdateRequest {
        private RequestStatus status;
        private String crew;
    }
}