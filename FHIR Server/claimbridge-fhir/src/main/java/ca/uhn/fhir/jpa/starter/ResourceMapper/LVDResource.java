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
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
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
public class LVDResource {

    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir";

    @PostMapping("/upload-lvd")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty!";
        }

        List<Procedure> procedures = parseLvdFile(file);
        return saveToFhirServer(procedures);
    }

    private List<Procedure> parseLvdFile(MultipartFile file) {
        List<Procedure> procedures = new ArrayList<>();
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

                for (int i = 0; i < headers.length; i++) {
                    rowData.put(headers[i], data[i]);
                }

                // System.out.println("Read Data: " + rowData); // ✅ Logging

                // สร้าง Procedure Resource
                Procedure procedure = new Procedure();

                // สร้าง CodeableConcept สำหรับ "LVD"
                CodeableConcept code = new CodeableConcept();
                code.setText("LVD"); // ตั้งข้อความตรง ๆ

                // กำหนด category ให้กับ Observation
                procedure.setCode(code);

                // procedure.setId("procedure-lvd-" + data[0]); // ตั้งค่า ID จากข้อมูลในไฟล์

                // ref AN
                procedure.setSubject(new Reference("Encounter?identifier=" + rowData.get("AN")));

                // กำหนด Period (ช่วงเวลาการทำการ)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HHmm", Locale.US);
                try {
                    Date startDate = sdf.parse(rowData.get("DATEIN") + " " + rowData.get("TIMEIN")); // DATEIN + TIMEIN
                    Date endDate = sdf.parse(rowData.get("DATEOUT") + " " + rowData.get("TIMEOUT")); // DATEOUT +
                                                                                                     // TIMEOUT

                    Period period = new Period();
                    period.setStartElement(new DateTimeType(startDate));
                    period.setEndElement(new DateTimeType(endDate));

                    procedure.setPerformed(period);

                } catch (Exception e) {
                    System.err.println("Error parsing dates: " + e.getMessage());
                    e.printStackTrace();
                }

                // สร้าง Extension จากข้อมูลใน JSON
                Extension extension1 = new Extension();
                // extension1.setUrl("http://example.org/fhir/StructureDefinition/QTYDAY");
                extension1.setValue(new org.hl7.fhir.r4.model.StringType(rowData.get("QTYDAY"))); // QTYDAY

                procedure.setExtension(List.of(extension1));

                // กำหนด text (เนื้อหาที่สร้างขึ้น)
                // String narrative = "<div
                // xmlns=\"http://www.w3.org/1999/xhtml\"><p><b>Generated Narrative:
                // Condition</b><a name=\"condition-opd-eclaim\"> </a></p><div style=\"display:
                // inline-block; background-color: #d9e0e7; padding: 6px; margin: 4px; border:
                // 1px solid #8da1b4; border-radius: 5px; line-height: 60%\"><p
                // style=\"margin-bottom: 0px\">Resource Condition
                // &quot;condition-opd-eclaim&quot; </p><p style=\"margin-bottom:
                // 0px\">Information Source: eclaim!</p><p style=\"margin-bottom: 0px\">Profile:
                // <a href=\"StructureDefinition-claimcon-condition-base.html\">ClaimCon
                // Condition</a></p></div><p><b>category</b>: Encounter Diagnosis <span
                // style=\"background: LightGoldenRodYellow; margin: 4px; border: 1px solid
                // khaki\"> (<a
                // href=\"http://terminology.hl7.org/5.0.0/CodeSystem-condition-category.html\">Condition
                // Category Codes</a>#encounter-diagnosis)</span></p><p><b>code</b>:
                // Hyperlipidaemia, unspecified <span style=\"background: LightGoldenRodYellow;
                // margin: 4px; border: 1px solid khaki\"> (<a
                // href=\"http://terminology.hl7.org/5.0.0/CodeSystem-icd10.html\">ICD-10</a>#E785)</span></p><p><b>subject</b>:
                // <a href=\"Patient-patient-eclaim.html\">Patient/patient-eclaim</a> &quot;
                // ใจดี&quot;</p><p><b>encounter</b>: <a
                // href=\"Encounter-encounter-opd-eclaim.html\">Encounter/encounter-opd-eclaim</a></p><p><b>recordedDate</b>:
                // 2022-12-25 09:41:23+0700</p><p><b>asserter</b>:
                // <span>id:\u00a0123456</span></p></div>";
                // procedure.getText().setStatus(org.hl7.fhir.r4.model.Narrative.NarrativeStatus.GENERATED);
                // procedure.getText().setDivAsString(narrative);

                procedures.add(procedure);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
        }
        return procedures;
    }

    private long countProcedureByType(String claimTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("Procedure File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("code.text", "LVD"));
            return count;
        }
    }

    private String saveToFhirServer(List<Procedure> procedures) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

        List<String> responseList = new ArrayList<>();
        long existingLVDCount = countProcedureByType("LVD");
        int skipped = 0;

        for (Resource resource : procedures) {
            try {
                // ถ้ามี LVD อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
                if (skipped < existingLVDCount) {
                    skipped++;
                    continue;
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
        if (skipped >= existingLVDCount && existingLVDCount > 0) {
            System.out.println("❌ (LVD) Skipping upload " + (skipped) + " record, already in system.");
        }

        return responseList.toString();
    }

    private void saveToMongo(String json) {
        // ตัวอย่างการบันทึกข้อมูล Patient ลง MongoDB
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("Procedure File");

        // แปลง Patient เป็น JSON format ก่อนบันทึก
        Document document = Document.parse(json);

        // บันทึกข้อมูลลง MongoDB
        collection.insertOne(document);

        mongoClient.close();
    }

}
