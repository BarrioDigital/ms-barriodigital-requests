package com.barriodigital.requests.service;

import com.barriodigital.requests.entity.Request;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Notificación vía RabbitMQ
    public void sendNotificationCommand(Request request) {
        try {
            String msg = String.format("{\"requestId\":%d, \"status\":\"%s\", \"citizenId\":\"%s\"}",
                    request.getId(), request.getStatus(), request.getCitizenId());
            rabbitTemplate.convertAndSend("cmd.direct", "email.send", msg);
            log.info("Comando de notificación enviado a RabbitMQ: {}", msg);
        } catch (Exception e) {
            log.error("Error al enviar comando a RabbitMQ: {}", e.getMessage());
        }
    }

    // Streaming de eventos vía Kafka
    public void publishAuditEvent(Request request, String previousStatus) {
        try {
            String payload = String.format("{\"requestId\":%d, \"procedureId\":%d, \"oldStatus\":\"%s\", \"newStatus\":\"%s\", \"timestamp\":\"%s\"}",
                    request.getId(), request.getProcedureId(), previousStatus, request.getStatus(), request.getUpdatedAt());
            
            kafkaTemplate.send("requests.events", String.valueOf(request.getId()), payload);
            kafkaTemplate.send("audit.timeline", String.valueOf(request.getId()), payload);
            log.info("Evento publicado en Kafka (requests.events / audit.timeline): {}", payload);
        } catch (Exception e) {
            log.error("Error al publicar evento en Kafka: {}", e.getMessage());
        }
    }
}