package ca.uhn.fhir.jpa.starter.ResourceMapper;

import static com.mongodb.client.model.Filters.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
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
public class LABFUResource {

    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir";
    private static final Map<String, String> LABTEST_MAP = createLabTestMap();

    @PostMapping("/upload-labfu")
    public String uploadLabResults(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty!";
        }

        List<Observation> observations = parseLabfuFile(file);
        return saveToFhirServer(observations);
    }

    private List<Observation> parseLabfuFile(MultipartFile file) {
        List<Observation> observations = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            // boolean isHeader = true;
            String[] headers = reader.readLine().split("\\|");

            while ((line = reader.readLine()) != null) {
                String[] data = line.split("\\|");
                if (data.length < headers.length)
                    continue;

                // Map เก็บชื่อคอลัมน์ -> ค่าข้อมูล
                Map<String, String> rowData = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    rowData.put(headers[i], data[i]);
                }

                Observation observation = new Observation();

                // สร้าง CodeableConcept สำหรับ "LABFU"
                CodeableConcept code = new CodeableConcept();
                code.setText("LABFU"); // ตั้งข้อความตรง ๆ

                // กำหนด category ให้กับ Observation
                observation.addCategory(code);

                // observation.setId("labfu-" + data[1] + "-" + data[4]); // HN + SEQ เป็น ID
                observation.setStatus(Observation.ObservationStatus.FINAL);

                // รหัสสถานพยาบาล (HCODE)
                Identifier identifier = new Identifier();
                identifier.setSystem("https://terms.sil-th.org/hcode/");
                identifier.setValue(rowData.get("HCODE")); // HCODE
                observation.addIdentifier(identifier);

                // เชื่อมโยงกับผู้ป่วย (PERSON_ID)
                observation.setSubject(new Reference("Patient?identifier=" + rowData.get("PERSON_ID")));

                // กำหนดวันที่รับบริการ (DATESERV)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
                try {
                    Date date = sdf.parse(rowData.get("DATESERV"));
                    observation.setEffective(new DateTimeType(date));
                } catch (Exception e) {
                    e.printStackTrace();
                    observation.setEffective(new StringType("Invalid date format: " + rowData.get("DATESERV")));
                }

                // ตรวจสอบ LABTEST
                String labTestCode = rowData.get("LABTEST");
                if (!LABTEST_MAP.containsKey(labTestCode)) {
                    throw new IllegalArgumentException("Invalid LABTEST code: " + labTestCode);
                }
                observation.setCode(new CodeableConcept().setText(LABTEST_MAP.get(labTestCode)));

                // ตรวจสอบ LABRESULT
                String labResult = rowData.get("LABRESULT").trim();
                if ("0".equals(labResult)) {
                    observation.setValue(null);
                } else {
                    try {
                        observation.setValue(new Quantity().setValue(Double.parseDouble(labResult)));
                    } catch (NumberFormatException e) {
                        observation.setValue(new StringType("Invalid Result"));
                    }
                }

                observations.add(observation);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return observations;
    }

    private long countObservationByType(String observationTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("Observation File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("category.text", "LABFU"));
            return count;
        }
    }

    private String saveToFhirServer(List<Observation> observations) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);
        List<String> responseList = new ArrayList<>();
        // ดึงจำนวน ADP ที่มีอยู่ใน MongoDB
        long existingLABFUCount = countObservationByType("LABFU");
        int skipped = 0;

        for (Observation observation : observations) {
            // ถ้ามี CHAR อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
            if (skipped < existingLABFUCount) {
                skipped++;
                continue;
            }

            MethodOutcome outcome = client.create().resource(observation).execute();
            responseList.add("Created Observation ID: " + outcome.getId().getIdPart());

            // แปลง Patient เป็น JSON
            String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

            // บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
            saveToMongo(json);

        }

        if (skipped >= existingLABFUCount && existingLABFUCount > 0) {
            System.out.println("❌ (LABFU) Skipping upload " + (skipped) + " record, already in system.");
        }

        return responseList.toString();
    }

    private void saveToMongo(String json) {
        // ตัวอย่างการบันทึกข้อมูล Patient ลง MongoDB
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("Observation File");

        // แปลง Patient เป็น JSON format ก่อนบันทึก
        Document document = Document.parse(json);

        // บันทึกข้อมูลลง MongoDB
        collection.insertOne(document);

        mongoClient.close();
    }

    private static Map<String, String> createLabTestMap() {
        Map<String, String> map = new HashMap<>();
        map.put("01", "ตรวจน้ำตาลในเลือด จากหลอดเลือดดำ หลังอดอาหาร");
        map.put("02", "ตรวจน้ำตาลในเลือด จากหลอดเลือดดำ โดยไม่อดอาหาร");
        map.put("03", "ตรวจน้ำตาลในเลือด จากเส้นเลือดฝอย หลังอดอาหาร");
        map.put("04", "ตรวจน้ำตาลในเลือด จากเส้นเลือดฝอย โดยไม่อดอาหาร");
        map.put("05", "ตรวจ HbA1C");
        map.put("06", "ตรวจ Triglyceride");
        map.put("07", "ตรวจ Total Cholesterol");
        map.put("08", "ตรวจ HDL Cholesterol");
        map.put("09", "ตรวจ LDL Cholesterol");
        map.put("10", "ตรวจ BUN ในเลือด");
        map.put("11", "ตรวจ Creatinine ในเลือด");
        map.put("12", "ตรวจโปรตีน microalbumin ในปัสสาวะ");
        map.put("13", "ตรวจ CREATININE ในปัสสาวะ");
        map.put("14", "ตรวจโปรตีน macroalbumin ในปัสสาวะ");
        map.put("15", "ตรวจ eGFR (ใช้สูตร CKD-EPI formula)");
        map.put("16", "ตรวจ Hb");
        map.put("17", "ตรวจ UPCR (Urine protein creatinine ratio)");
        map.put("18", "ตรวจ K (สำหรับ CKD stage 3 ขึ้นไป หรือใช้ยา ACEI/ARBs)");
        map.put("19", "ตรวจ Bicarb (สำหรับ CKD stage 3 ขึ้นไป)");
        map.put("20", "ตรวจ phosphate (สำหรับ CKD stage 3 ขึ้นไป)");
        map.put("21", "ตรวจ PTH (สำหรับ CKD stage 3 ขึ้นไป)");
        return map;
    }
}
