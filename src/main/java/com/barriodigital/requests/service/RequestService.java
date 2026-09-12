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
        // 1. Intentar descontar cupo en ms-barriodigital-catalog
        try {
            String url = catalogUrl + "/procedures/" + request.getProcedureTypeId() + "/decrease-quota";
            restTemplate.put(url, null);
        } catch (HttpClientErrorException ex) {
            // Captura errores 40x de ms-catalog y extrae limpiamente el mensaje de negocio
            String responseBody = ex.getResponseBodyAsString();
            String cleanMessage = extractMessageFromJson(responseBody);
            throw new IllegalArgumentException(cleanMessage);
        } catch (Exception e) {
            throw new RuntimeException("Error al comunicarse con el microservicio de Catálogo: " + e.getMessage());
        }

        // 2. Si el descuento es exitoso, se guarda la solicitud
        request.setStatus(RequestStatus.INGRESADO);
        RequestEntity saved = repository.save(request);
        
        // 3. Notificación de eventos
        publishKafkaEvent("requests.events", saved, null);
        publishKafkaEvent("audit.timeline", saved, null);
        
        return saved;
    }

    @Transactional
    public RequestEntity updateStatus(Long requestId, RequestStatus newStatus, String crew) {
        RequestEntity request = getById(requestId);
        RequestStatus currentStatus = request.getStatus();

        // Regla de Negocio Crítica
        if (newStatus == RequestStatus.EN_TERRENO && currentStatus != RequestStatus.ADMITIDO) {
            throw new IllegalArgumentException("No se puede pasar a EN_TERRENO sin haber pasado antes por ADMITIDO");
        }

        request.setStatus(newStatus);
        if (crew != null) {
            request.setAssignedCrew(crew);
        }

        RequestEntity updated = repository.save(request);

        // Notificación vía RabbitMQ (Protegida)
        try {
            Map<String, Object> notifyMessage = new HashMap<>();
            notifyMessage.put("requestId", updated.getId());
            notifyMessage.put("status", updated.getStatus().name());
            notifyMessage.put("citizenId", updated.getCitizenId());

            rabbitTemplate.convertAndSend("cmd.direct", "email.send", notifyMessage);
        } catch (Exception e) {
            System.err.println("Advertencia: No se pudo enviar el mensaje a RabbitMQ: " + e.getMessage());
        }

        // Publicación de eventos en Kafka (Protegida)
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

    // Método de soporte para extraer únicamente la descripción útil del error JSON
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