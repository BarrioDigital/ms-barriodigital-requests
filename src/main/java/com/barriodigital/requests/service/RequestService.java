package com.barriodigital.requests.service;

import com.barriodigital.requests.model.RequestEntity;
import com.barriodigital.requests.model.RequestStatus;
import com.barriodigital.requests.repository.RequestRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RequestService {

    @Autowired
    private RequestRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${services.catalog-url}")
    private String catalogUrl;

    public List<RequestEntity> getAllRequests() {
        return repository.findAll();
    }

    public RequestEntity getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trámite no encontrado con ID: " + id));
    }

    @Transactional
    public RequestEntity createRequest(RequestEntity request) {
        try {
            String url = catalogUrl + "/procedures/" + request.getProcedureTypeId() + "/decrease-quota";
            restTemplate.put(url, null);
        } catch (HttpClientErrorException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String cleanMessage = extractMessageFromJson(responseBody);
            throw new IllegalArgumentException(cleanMessage);
        } catch (Exception e) {
            throw new RuntimeException("Error al comunicarse con el microservicio de Catálogo: " + e.getMessage());
        }

        request.setStatus(RequestStatus.INGRESADO);
        RequestEntity saved = repository.save(request);

        publishKafkaEvent("requests.events", saved, null);
        publishKafkaEvent("audit.timeline", saved, null);
        
        publishRabbitNotification(saved, "email.send", "Solicitud ingresada con éxito.");

        return saved;
    }

    @Transactional
    public RequestEntity updateStatus(Long requestId, RequestStatus newStatus, String crew) {
        RequestEntity request = getById(requestId);
        RequestStatus currentStatus = request.getStatus();

        if (newStatus == RequestStatus.EN_TERRENO && currentStatus != RequestStatus.ADMITIDO) {
            throw new IllegalArgumentException("No se puede pasar a EN_TERRENO sin haber pasado antes por ADMITIDO");
        }

        request.setStatus(newStatus);
        if (crew != null) {
            request.setAssignedCrew(crew);
        }

        RequestEntity updated = repository.save(request);

        if (newStatus == RequestStatus.ADMITIDO) {
            publishRabbitNotification(updated, "email.send", "Tu solicitud ha sido ADMITIDA.");
        } else if (newStatus == RequestStatus.EN_TERRENO) {
            publishRabbitNotification(updated, "crew.ticket", "Asignación de terreno a cuadrilla: " + updated.getAssignedCrew());
        } else if (newStatus == RequestStatus.RESUELTO) {
            publishRabbitNotification(updated, "certificate.gen", "Generación de certificado de resolución.");
            publishRabbitNotification(updated, "email.send", "Tu solicitud ha sido RESUELTA.");
        }

        publishKafkaEvent("requests.events", updated, currentStatus);
        publishKafkaEvent("audit.timeline", updated, currentStatus);

        return updated;
    }

    private void publishKafkaEvent(String topic, RequestEntity entity, RequestStatus oldStatus) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("requestId", entity.getId());
            event.put("oldStatus", oldStatus != null ? oldStatus.name() : null);
            event.put("newStatus", entity.getStatus().name());
            event.put("citizenId", entity.getCitizenId());
            event.put("timestamp", System.currentTimeMillis());

            kafkaTemplate.send(topic, String.valueOf(entity.getId()), event);
        } catch (Exception e) {
            System.err.println("Advertencia: No se pudo publicar el evento en Kafka (" + topic + "): " + e.getMessage());
        }
    }

    private void publishRabbitNotification(RequestEntity request, String routingKey, String taskDetails) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("requestId", request.getId());
            payload.put("status", request.getStatus().name());
            payload.put("citizenId", request.getCitizenId());
            payload.put("taskDetails", taskDetails);

            Map<String, Object> envelope = new HashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("traceId", UUID.randomUUID().toString());
            envelope.put("timestamp", new java.util.Date().toString());
            envelope.put("data", payload);

            rabbitTemplate.convertAndSend("cmd.direct", routingKey, envelope.toString());
        } catch (Exception e) {
            System.err.println("Error al publicar en RabbitMQ: " + e.getMessage());
        }
    }

    private String extractMessageFromJson(String json) {
        if (json == null || json.isBlank()) {
            return "Error de validación o regla de negocio en el catálogo";
        }
        if (json.contains("\"message\":\"")) {
            int start = json.indexOf("\"message\":\"") + 11;
            int end = json.indexOf("\"", start);
            if (end > start) {
                return json.substring(start, end);
            }
        }
        return json;
    }
}