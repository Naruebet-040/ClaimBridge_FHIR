package ca.uhn.fhir.jpa.starter.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

@RestController
@RequestMapping("/mongo")
public class MongoTestController {

    @Autowired
    private MongoTemplate mongoTemplate;

    @GetMapping("/test-connection")
    public String testConnection() {
        try {
            Set<String> collections = mongoTemplate.getCollectionNames();
            return "Connected to MongoDB! Collections: " + collections;
        } catch (Exception e) {
            return "MongoDB Connection Failed: " + e.getMessage();
        }
    }
}
