package ca.uhn.fhir.jpa.starter.ResourceMapper;

import static com.mongodb.client.model.Filters.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@RestController
@RequestMapping("/api/fhir")
public class XRFResource {

    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir";

    @PostMapping("/upload-rf")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty!";
        }

        List<Resource> resources = parseTextFile(file);
        return saveToFhirServer(resources);
    }

    private List<Resource> parseTextFile(MultipartFile file) {
        List<Resource> resources = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;

            // อ่าน Header
            String[] headers = br.readLine().split("\\|");

            while ((line = br.readLine()) != null) {
                String[] data = line.split("\\|");
                if (data.length < headers.length)
                    continue;

                // Map เก็บชื่อคอลัมน์ -> ค่าข้อมูล
                Map<String, String> rowData = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    rowData.put(headers[i], data[i]);
                }

                // สร้าง ServiceRequest (FHIR)
                ServiceRequest serviceRequest = new ServiceRequest();

                // ตั้งค่า Identifier (SEQ)
                // serviceRequest.addIdentifier(new Identifier()
                // .setSystem("https://example.org/referral-id")
                // .setValue(data[5])); // SEQ

                // *** ส่วนของเฉพาะ ORF ***//
                if (rowData.get("HN") != null) {
                    // สร้าง CodeableConcept สำหรับ "ORF"
                    CodeableConcept code = new CodeableConcept();
                    code.setText("ORF");

                    // กำหนดให้กับประเภท (category) หรือ field อื่นๆ ของ ServiceRequest
                    serviceRequest.addCategory(code); // หรือใช้ field อื่นๆ เช่น reasonCode ตามที่เหมาะสม

                    // ตั้งค่า Subject (Patient)
                    serviceRequest.setSubject(new Reference("Patient?identifier=" + rowData.get("HN"))); // HN

                    // ตั้งค่า วันที่คำขอ
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                    try {
                        Date requestDate = sdf.parse(rowData.get("DATEOPD")); // DATEOPD
                        serviceRequest.setAuthoredOn(requestDate);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    // ตั้งค่าผู้ขอ (คลินิก)
                    // serviceRequest.setRequester(new Reference("Organization?identifier=" +
                    // rowData.get("CLINIC"))); //
                    // CLINIC

                    // ตั้งค่าวันที่ส่งต่อ (REFERDATE)
                    try {
                        Date referDate = sdf.parse(rowData.get("REFERDATE")); // REFERDATE
                        serviceRequest.setOccurrence(new org.hl7.fhir.r4.model.DateTimeType(referDate));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }

                // *** ส่วนของเฉพาะ IRF ***//
                if (rowData.get("AN") != null) {
                    // สร้าง CodeableConcept สำหรับ "IRF"
                    CodeableConcept code = new CodeableConcept();
                    code.setText("IRF");

                    // กำหนดให้กับประเภท (category) หรือ field อื่นๆ ของ ServiceRequest
                    serviceRequest.addCategory(code); // หรือใช้ field อื่นๆ เช่น reasonCode ตามที่เหมาะสม

                    serviceRequest.setSubject(new Reference("Encounter?identifier=" + rowData.get("AN"))); // AN
                }

                // *** ส่วนที่ใช้ร่วมกันของ IPD+OPD ***//
                // ตั้งค่าหน่วยบริการที่รับการส่งต่อ
                serviceRequest.addPerformer(new Reference("Organization?identifier=" + rowData.get("REFER"))); //
                // REFER

                // ตั้งค่าประเภทการส่งต่อ
                serviceRequest.addCategory().addCoding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/service-category")
                        .setCode("referral")
                        .setDisplay("Referral"); // set

                resources.add(serviceRequest);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
        return resources;
    }

    private long countIRFServiceRequestsByType(String claimTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("ServiceRequest File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("category.text", "IRF"));
            return count;
        }
    }

    private long countORFServiceRequestsByType(String claimTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("ServiceRequest File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("category.text", "ORF"));
            return count;
        }
    }

    private String saveToFhirServer(List<Resource> resources) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

        List<String> responseList = new ArrayList<>();
        long existingIRFCount = countIRFServiceRequestsByType("IRF");
        long existingORFCount = countORFServiceRequestsByType("ORF");

        int irf_skipped = 0;
        int orf_skipped = 0;

        for (Resource resource : resources) {
            try {
                if (resource instanceof ServiceRequest) {
                    ServiceRequest serviceRequest = (ServiceRequest) resource;

                    // เข้าถึงข้อความของ category
                    CodeableConcept code = serviceRequest.getCategory().get(0); // สมมุติว่า category เป็น List

                    if (code.getText().equals("IRF")) {
                        // ถ้ามี IRF อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
                        if (irf_skipped < existingIRFCount) {
                            irf_skipped++;
                            continue;
                        }
                    } else if (code.getText().equals("ORF")) {
                        // ถ้ามี ORF อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
                        if (orf_skipped < existingORFCount) {
                            orf_skipped++;
                            continue;
                        }
                    }

                }

                // เช็ค Resource ก่อน
                MethodOutcome outcome = client.create().resource(resource).execute();
                responseList.add("Created Resource ID: " + outcome.getId().getIdPart());

                // แปลง Patient เป็น JSON
                String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

                // บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
                saveToMongo(json);

            } catch (Exception e) {
                System.err.println("Error saving to FHIR server: " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (irf_skipped >= existingIRFCount && existingIRFCount > 0) {
            System.out.println("❌ (IRF) Skipping upload " + (irf_skipped) + " record, already in system.");
        }
        if (orf_skipped >= existingORFCount && existingORFCount > 0) {
            System.out.println("❌ (ORF) Skipping upload " + (orf_skipped) + " record, already in system.");
        }

        return responseList.toString();
    }

    private void saveToMongo(String json) {
        // ตัวอย่างการบันทึกข้อมูล Patient ลง MongoDB
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("ServiceRequest File");

        // แปลง Patient เป็น JSON format ก่อนบันทึก
        Document document = Document.parse(json);

        // บันทึกข้อมูลลง MongoDB
        collection.insertOne(document);

        mongoClient.close();
    }

}
