package com.adyen.checkout.api;

import com.adyen.checkout.ApplicationProperty;
import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SignatureException;

/**
 * REST controller for receiving Adyen webhook notifications
 */
@RestController
@RequestMapping("/api")
public class WebhookResource {
    private final Logger log = LoggerFactory.getLogger(WebhookResource.class);

    private ApplicationProperty applicationProperty;

    @Autowired
    public WebhookResource(ApplicationProperty applicationProperty) {
        this.applicationProperty = applicationProperty;

        if (this.applicationProperty.getHmacKey() == null) {
            log.warn("ADYEN_HMAC_KEY is UNDEFINED (Webhook cannot be authenticated)");
            //throw new RuntimeException("ADYEN_HMAC_KEY is UNDEFINED");
        }
    }

    /**
     * Process incoming Webhook notification: get NotificationRequestItem, validate HMAC signature,
     * consume the event asynchronously, send response ["accepted"]
     *
     * @param notificationRequest
     * @return
     */
    @PostMapping("/webhooks/notifications")
    public ResponseEntity<String> webhooks(@RequestBody NotificationRequest notificationRequest) {

        // fetch first (and only) NotificationRequestItem
        var notificationRequestItem = notificationRequest.getNotificationItems().stream().findFirst();

        if (notificationRequestItem.isPresent()) {

            var item = notificationRequestItem.get();

            try {
                if (getHmacValidator().validateHMAC(item, this.applicationProperty.getHmacKey())) {
                    log.info("Received webhook with event {} : \n" +
                                    "Merchant Reference: {}\n" +
                                    "Alias : {}\n" +
                                    "PSP reference : {}"
                            , item.getEventCode(), item.getMerchantReference(), item.getAdditionalData().get("alias"), item.getPspReference());

                    // consume event asynchronously
                    consumeEvent(item);

                } else {
                    // invalid HMAC signature: do not send [accepted] response
                    log.warn("Could not validate HMAC signature for incoming webhook message: {}", item);
                    throw new RuntimeException("Invalid HMAC signature");
                }
            } catch (SignatureException e) {
                // Unexpected error during HMAC validation: do not send [accepted] response
                log.error("Error while validating HMAC Key", e);
            }

        } else {
            // Unexpected event with no payload: do not send [accepted] response
            log.warn("Empty NotificationItem");
        }

        // Acknowledge event has been consumed
        return ResponseEntity.ok().body("[accepted]");
    }

    // process payload asynchronously
    void consumeEvent(NotificationRequestItem item) {
        // add item to DB, queue or different thread

        // example: send to Kafka consumer

        // producer.send(producerRecord);
        // producer.flush();
        // producer.close();

    }

    @Bean
    public HMACValidator getHmacValidator() {
        return new HMACValidator();
    }
}
