package ca.uhn.fhir.jpa.starter.ResourceMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
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
public class OPDResource {

    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir";

    @PostMapping("/upload-opd")
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

                // ✅ สร้าง Encounter
                Encounter encounter = new Encounter();

                CodeableConcept type = new CodeableConcept();
                type.setText("OPD");
                encounter.addType(type);

                // 🔹 หมายเลข HN
                encounter.setSubject(new Reference("Patient?identifier=" + rowData.get("HN"))); // HN

                encounter.addType().addCoding(new Coding()
                        .setSystem("http://example.org/encounter-type")
                        .setCode(rowData.get("CLINIC")) // CLINIC
                        .setDisplay("Clinic"));

                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                try {
                    Date dateOpd = sdf.parse(rowData.get("DATEOPD")); // DATEOPD
                    Period period = new Period();
                    period.setStart(dateOpd);

                    encounter.setPeriod(period);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                resources.add(encounter);

                // ✅ สร้าง Observations
                /*
                 * resources.add(createObservation("SBP", rowData.get("SBP"), "2710-2",
                 * "Systolic Blood Pressure", "mmHg",
                 * rowData.get("HN")));
                 * resources.add(createObservation("DBP", rowData.get("DBP"), "8462-4",
                 * "Diastolic Blood Pressure", "mmHg",
                 * rowData.get("HN")));
                 * resources.add(
                 * createObservation("PR", rowData.get("PR"), "8867-4", "Pulse Rate", "bpm",
                 * rowData.get("HN")));
                 * resources.add(createObservation("RR", rowData.get("RR"), "9279-1",
                 * "Respiratory Rate", "bpm",
                 * rowData.get("HN")));
                 */
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
        return resources;
    }

    private Observation createObservation(String fieldName, String value, String loincCode, String display, String unit,
            String encounterId) {
        Observation observation = new Observation();
        observation.setCode(new CodeableConcept().addCoding(new Coding()
                .setSystem("http://loinc.org")
                .setCode(loincCode)
                .setDisplay(display)));

        try {
            Double numericValue = Double.parseDouble(value);
            observation.setValue(new Quantity().setValue(numericValue).setUnit(unit));
        } catch (Exception e) {
            e.printStackTrace();
        }

        observation.setEncounter(new Reference("Encounter?subject=" + encounterId));
        // // ลิงก์กับ Encounter
        return observation;
    }

    // ฟังก์ชันใหม่ในการตรวจสอบข้อมูลซ้ำ
    private boolean isDuplicatePatientRef(String encounterRef) {
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("Encounter File");

        // ค้นหาข้อมูล Patient ใน MongoDB โดยใช้รหัสประจำตัว (HN หรือ CID)
        Document existingDocument = collection.find(new Document("subject.reference", encounterRef)).first();

        mongoClient.close();

        return existingDocument != null;
    }

    private String findPatientIdByHN(String hn) {
        try (MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("Patient File");

            // ค้นหาข้อมูล Patient โดยใช้ HN
            Document patientDoc = collection.find(new Document("identifier.value", hn)).first();

            // หากพบเอกสารจะดึงค่า "id" จากฟิลด์ "id"
            if (patientDoc != null) {
                return patientDoc.getString("id"); // คืนค่าจากฟิลด์ "id"
            } else {
                return null; // ถ้าไม่พบข้อมูล
            }
        } catch (Exception e) {
            System.err.println("Error finding patient ID by HN: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String saveToFhirServer(List<Resource> resources) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

        List<String> responseList = new ArrayList<>();
        for (Resource resource : resources) {
            try {

                // ตรวจสอบเฉพาะ Encounter ว่าซ้ำหรือไม่
                if (resource instanceof Encounter) {
                    Encounter encounter = (Encounter) resource;
                    String patientReference = encounter.getSubject().getReference();

                    // ตรวจสอบว่า patientReference มี '?' และแยก identifier ออก
                    if (patientReference != null && patientReference.contains("?identifier=")) {
                        String hn = patientReference.split("\\?identifier=")[1];

                        // Log
                        // System.out.println("Patient ID to check: " + hn);
                        // System.out.println("Patient/" + findPatientIdByHN(hn));

                        // Setid
                        String patientId = "Patient/" + findPatientIdByHN(hn);
                        // ตรวจสอบว่า patientId ซ้ำหรือไม่ใน MongoDB
                        if (isDuplicatePatientRef(patientId)) {
                            System.out
                                    .println("❌ (OPD) HN: " + hn + " is already duplicated in the system!");
                            continue; // ข้ามการอัปโหลด
                        }
                    } else {
                        System.out.println("❌ Invalid patient reference format: " + patientReference);
                        continue; // ข้ามการอัปโหลดหากข้อมูลไม่ถูกต้อง
                    }
                }

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
        return responseList.toString();
    }

    private void saveToMongo(String json) {
        // ตัวอย่างการบันทึกข้อมูล Patient ลง MongoDB
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("Encounter File");

        // แปลง Patient เป็น JSON format ก่อนบันทึก
        Document document = Document.parse(json);

        // บันทึกข้อมูลลง MongoDB
        collection.insertOne(document);

        mongoClient.close();
    }
}
