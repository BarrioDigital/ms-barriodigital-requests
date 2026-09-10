package com.barriodigital.requests.service;

import com.barriodigital.requests.entity.Request;
import com.barriodigital.requests.model.RequestStatus;
import com.barriodigital.requests.repository.RequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final EventPublisherService eventPublisherService;

    public List<Request> findAll() {
        return requestRepository.findAll();
    }

    public Optional<Request> findById(Long id) {
        return requestRepository.findById(id);
    }

    public Request createRequest(Request request) {
        request.setStatus(RequestStatus.INGRESADO);
        Request saved = requestRepository.save(request);
        
        // Notificar y auditar
        eventPublisherService.sendNotificationCommand(saved);
        eventPublisherService.publishAuditEvent(saved, "NONE");
        
        return saved;
    }

    public Optional<Request> updateStatus(Long id, RequestStatus newStatus, String crew) {
        return requestRepository.findById(id).map(req -> {
            RequestStatus currentStatus = req.getStatus();

            // Regla de negocio: No se puede pasar a EN_TERRENO sin estar ADMITIDO
            if (newStatus == RequestStatus.EN_TERRENO && currentStatus != RequestStatus.ADMITIDO) {
                throw new IllegalArgumentException("Regla de negocio violada: No se puede cambiar a EN_TERRENO sin pasar previamente por ADMITIDO.");
            }

            req.setStatus(newStatus);
            if (crew != null && !crew.isBlank()) {
                req.setAssignedCrew(crew);
            }

            Request updated = requestRepository.save(req);

            // Publicar eventos a colas/tópicos
            eventPublisherService.sendNotificationCommand(updated);
            eventPublisherService.publishAuditEvent(updated, currentStatus.name());

            return updated;
        });
    }

    public boolean deleteById(Long id) {
        if (requestRepository.existsById(id)) {
            requestRepository.deleteById(id);
            return true;
        }
        return false;
    }
}