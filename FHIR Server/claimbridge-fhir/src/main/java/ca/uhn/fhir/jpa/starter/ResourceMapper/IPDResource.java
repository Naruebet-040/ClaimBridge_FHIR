package ca.uhn.fhir.jpa.starter.ResourceMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bson.Document;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
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
public class IPDResource {

    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir";

    @PostMapping("/upload-ipd")
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
                type.setText("IPD"); // ตั้งข้อความตรง ๆ โดยไม่ต้องใช้ Coding
                encounter.addType(type);

                // 🔹 Patient Reference (HN)
                String hn = rowData.get("HN");
                if (Objects.nonNull(hn) && !hn.isEmpty()) {
                    encounter.setSubject(new Reference("Patient?identifier=" + hn));
                }

                // 🔹 Identifier (AN)
                String an = rowData.get("AN");
                if (Objects.nonNull(an) && !an.isEmpty()) {
                    encounter.addIdentifier(new Identifier().setSystem("https://example.org/an").setValue(an));
                }

                // 🔹 Period (Admission - Discharge)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HHmm");
                Period period = new Period();

                String dateAdm = rowData.get("DATEADM");
                String timeAdm = rowData.get("TIMEADM");
                if (Objects.nonNull(dateAdm) && !dateAdm.isEmpty()) {
                    try {
                        Date startDate = sdf.parse(dateAdm + " " + timeAdm);
                        period.setStart(startDate);
                    } catch (ParseException e) {
                        System.err.println("Invalid DATEADM/TIMEADM format: " + dateAdm + " " + timeAdm);
                    }
                }

                String dateDsc = rowData.get("DATEDSC");
                String timeDsc = rowData.get("TIMEDSC");
                if (Objects.nonNull(dateDsc) && !dateDsc.isEmpty()) {
                    try {
                        Date endDate = sdf.parse(dateDsc + " " + timeDsc);
                        period.setEnd(endDate);
                    } catch (ParseException e) {
                        System.err.println("Invalid DATEDSC/TIMEDSC format: " + dateDsc + " " + timeDsc);
                    }
                }

                encounter.setPeriod(period);

                // 🔹 Discharge Status
                String dischs = rowData.get("DISCHS");
                if (Objects.nonNull(dischs) && !dischs.isEmpty()) {
                    encounter.setStatus(Encounter.EncounterStatus.FINISHED);
                }

                // 🔹 Discharge Type
                String discht = rowData.get("DISCHT");
                if (Objects.nonNull(discht) && !discht.isEmpty()) {
                    encounter.getHospitalization().setDischargeDisposition(
                            new CodeableConcept().setText(discht));
                }

                // 🔹 Ward (Location)
                /*
                 * String ward = rowData.get("WARDDSC");
                 * if (Objects.nonNull(ward) && !ward.isEmpty()) {
                 * encounter.addLocation(new Encounter.EncounterLocationComponent()
                 * .setLocation(new Reference("Location/" + ward)));
                 * }
                 */

                // 🔹 Admit Source
                String admW = rowData.get("ADM_W");
                if (Objects.nonNull(admW) && !admW.isEmpty()) {
                    encounter.getHospitalization().setAdmitSource(new CodeableConcept().setText(admW));
                }

                // 🔹 Special Courtesy (UUC)
                String uuc = rowData.get("UUC");
                if (Objects.nonNull(uuc) && !uuc.isEmpty()) {
                    encounter.getHospitalization().setSpecialCourtesy(
                            Collections.singletonList(new CodeableConcept().setText(uuc)));
                }

                // 🔹 Service Type
                String svcType = rowData.get("SVCTYPE");
                if (Objects.nonNull(svcType) && !svcType.isEmpty()) {
                    encounter.setType(Collections.singletonList(
                            new CodeableConcept().setText(svcType)));
                }

                resources.add(encounter);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        return resources;
    }

    // ฟังก์ชันใหม่ในการตรวจสอบข้อมูลซ้ำ
    private boolean isDuplicateEncounter(String encounterId) {
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("Encounter File");

        // ค้นหาข้อมูล Patient ใน MongoDB โดยใช้รหัสประจำตัว (HN หรือ CID)
        Document existingDocument = collection.find(new Document("identifier.value", encounterId)).first();

        mongoClient.close();

        return existingDocument != null;
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
                    String an = encounter.getIdentifierFirstRep().getValue(); // ✅ ถูกต้องแล้ว

                    if (isDuplicateEncounter(an)) {
                        System.out.println("❌ (IPD) AN: " + an + " is already duplicated in the system!");
                        continue;
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
