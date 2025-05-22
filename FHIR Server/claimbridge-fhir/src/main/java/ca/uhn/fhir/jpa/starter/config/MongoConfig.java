package ca.uhn.fhir.jpa.starter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

@Configuration
public class MongoConfig {

    @Bean
    public MongoTemplate mongoTemplate() {
        // กำหนด URL เชื่อมต่อ MongoDB
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://ClaimBridgeAdmin:Fl3zn0~G;A28iCL@claimbridge.dpfel8b.mongodb.net/");
        // ตั้งค่า MongoTemplate กับฐานข้อมูลที่ต้องการ
        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(mongoClient, "FHIR_Resource"));
    }
}
