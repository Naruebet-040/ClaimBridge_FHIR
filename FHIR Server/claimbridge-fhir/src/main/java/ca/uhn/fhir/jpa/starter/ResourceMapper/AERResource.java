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
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Period;
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
public class AERResource {

    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir";

    @PostMapping("/upload-aer")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty!";
        }

        List<Encounter> encounters = parseTextFile(file);
        return saveToFhirServer(encounters);
    }

    // ฟังก์ชันสำหรับแปลงข้อมูลในไฟล์เป็น Encounter
    private List<Encounter> parseTextFile(MultipartFile file) {
        List<Encounter> encounters = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            // boolean isHeader = true;
            String[] headers = reader.readLine().split("\\|", -1);

            while ((line = reader.readLine()) != null) {
                String[] data = line.split("\\|", -1);
                if (data.length < headers.length)
                    continue;

                // Map เก็บชื่อคอลัมน์ -> ค่าข้อมูล
                Map<String, String> rowData = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    rowData.put(headers[i], data[i]);
                }

                Encounter encounter = new Encounter();

                // สร้าง CodeableConcept สำหรับ "AER"
                CodeableConcept code = new CodeableConcept();
                code.setText("AER"); // ตั้งข้อความตรง ๆ

                // เพิ่ม code เข้าไปใน type ของ Encounter
                encounter.addType(code);

                encounter.setId("encounter-" + data[0]); // ID จากข้อมูล
                encounter.getMeta().setSource("eclaim");

                // ref AN
                if (rowData.get("AN") != null && !rowData.get("AN").toString().trim().isEmpty()) {
                    encounter.setSubject(new Reference("Encounter?identifier=" + rowData.get("AN")));
                } else {
                    // ref HN
                    encounter.setSubject(new Reference("Patient?identifier=" + rowData.get("HN")));
                }

                // DATEOPD
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
                try {
                    Date DateOPD = sdf.parse(rowData.get("DATEOPD")); // DATEOPD

                    Period period = new Period();
                    period.setStartElement(new DateTimeType(DateOPD));
                    encounter.setPeriod(period);
                } catch (Exception e) {
                    // e.printStackTrace();
                }

                // วัน-เวลาที่เกิดเหตุ
                SimpleDateFormat sdfInput = new SimpleDateFormat("yyyyMMdd HHss", Locale.US);
                try {
                    Date startDate = sdfInput.parse(rowData.get("AEDATE") + " " + rowData.get("AETIME")); // AEDATE +
                                                                                                          // AETIME

                    Period period = new Period();
                    period.setStartElement(new DateTimeType(startDate));

                    encounter.setPeriod(period);

                } catch (Exception e) {
                    System.err.println("Error parsing dates: " + e.getMessage());
                    e.printStackTrace();
                }

                // AETYPE
                if (!data[6].isEmpty()) {
                    String aetypeCode = data[6];
                    Extension aetypeExtension = new Extension();
                    aetypeExtension.setUrl("https://terms.sil-th.org/core/ValueSet/vs-eclaim-accident-coverage");
                    aetypeExtension.setValue(new StringType(getAEType(aetypeCode)));
                    encounter.addExtension(aetypeExtension);
                }

                // Encounter Identifier (REFER_NO)
                if (!rowData.get("REFER_NO").isEmpty()) {
                    Identifier identifier = new Identifier();
                    identifier.setValue(rowData.get("REFER_NO"));
                    encounter.addIdentifier(identifier);
                }

                // IREFTYPE
                if (!rowData.get("IREFTYPE").isEmpty()) {
                    String ireftypeCode = rowData.get("IREFTYPE");
                    Extension ireftypeExtension = new Extension();
                    ireftypeExtension.setUrl("https://terms.sil-th.org/core/ValueSet/vs-eclaim-refer-reason");
                    ireftypeExtension.setValue(new StringType(getIREFType(ireftypeCode)));
                    encounter.addExtension(ireftypeExtension);
                }

                // OREFTYPE
                if (!rowData.get("OREFTYPE").isEmpty()) {
                    String oreftypeCode = rowData.get("OREFTYPE");
                    Extension oreftypeExtension = new Extension();
                    oreftypeExtension.setUrl("https://terms.sil-th.org/core/ValueSet/vs-eclaim-refer-reason");
                    oreftypeExtension.setValue(new StringType(getIREFType(oreftypeCode)));
                    encounter.addExtension(oreftypeExtension);
                }

                // UCAE
                if (!rowData.get("UCAE").isEmpty()) {
                    String ucaeCode = rowData.get("UCAE");
                    Extension ucaeExtension = new Extension();
                    ucaeExtension.setUrl("https://terms.sil-th.org/core/CodeSystem/cs-eclaim-refer-type-eclaim");
                    ucaeExtension.setValue(new StringType(getUCAE(ucaeCode)));
                    encounter.addExtension(ucaeExtension);
                }

                // EMTYPE
                if (!rowData.get("EMTYPE").isEmpty()) {
                    String emtypeCode = rowData.get("EMTYPE");
                    Extension emtypeExtension = new Extension();
                    emtypeExtension.setUrl("https://terms.sil-th.org/core/ValueSet/vs-eclaim-refer-priority-code");
                    emtypeExtension.setValue(new StringType(getEMType(emtypeCode)));
                    encounter.addExtension(emtypeExtension);
                }

                // SEQ
                // if (!data[15].isEmpty()) {
                // Identifier seq = new Identifier();
                // seq.setSystem("https://example.com/fhir/seqsystem");
                // seq.setValue(data[15]);
                // encounter.addIdentifier(seq);
                // }

                // Status ของ Encounter
                encounter.setStatus(Encounter.EncounterStatus.FINISHED);

                encounters.add(encounter);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return encounters;
    }

    private long countEncounterByType(String encounterTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("Encounter File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("type.text", "AER"));
            return count;
        }
    }

    // ฟังก์ชันสำหรับการส่งข้อมูลไปยัง FHIR Server
    private String saveToFhirServer(List<Encounter> Encounter) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

        List<String> responseList = new ArrayList<>();
        long existingAERCount = countEncounterByType("AER");
        int skipped = 0;

        for (Encounter encounter : Encounter) {
            // ถ้ามี LVD อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
            if (skipped < existingAERCount) {
                skipped++;
                continue;
            }

            MethodOutcome outcome = client.create().resource(encounter).execute();
            responseList.add("Created Encounter ID: " + outcome.getId().getIdPart());

            // แปลง Patient เป็น JSON
            String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

            // บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
            saveToMongo(json);
        }

        if (skipped >= existingAERCount && existingAERCount > 0) {
            System.out.println("❌ (AER) Skipping upload " + (skipped) + " record, already in system.");
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

    // ตรวจสอบว่า AETYPE เป็นค่าที่ถูกต้องหรือไม่
    private String getAEType(String aetype) {
        switch (aetype) {
            case "V":
                return "ใช้ พรบ.ผู้ประสบภัยจากรถ";
            case "O":
                return "ใช้ พรบ.กองทุนเงินทุดแทน";
            case "B":
                return "ใช้ทั้ง พรบ.ผู้ประสบภัยจากรถ และ พรบ.กองทุนเงินทุดแทน";
            default:
                return "รหัสไม่ตรงกับที่กำหนด";
        }
    }

    // ตรวจสอบว่า IREFTYPE เป็นค่าที่ถูกต้องหรือไม่
    private String getIREFType(String ireftype) {
        switch (ireftype) {
            case "0001":
                return "ตามความต้องการของผู้ป่วย";
            case "0010":
                return "รับไว้รักษาต่อเนื่อง";
            case "0011":
                return "รับไว้รักษาต่อเนื่อง และตามความต้องการของผู้ป่วย";
            case "0100":
                return "รักษา";
            case "0101":
                return "รักษา และตามความต้องการของผู้ป่วย";
            case "0110":
                return "รักษา และรับไว้รักษาต่อเนื่อง";
            case "0111":
                return "รักษา รับไว้รักษาต่อเนื่อง และตามความต้องการของผู้ป่วย";
            case "1000":
                return "วินิจฉัย";
            case "1001":
                return "วินิจฉัย และตามความต้องการของผู้ป่วย";
            case "1010":
                return "วินิจฉัย และรับไว้รักษาต่อเนื่อง";
            case "1011":
                return "วินิจฉัย รับไว้รักษาต่อเนื่อง และตามความต้องการของผู้ป่วย";
            case "1100":
                return "วินิจฉัย และรักษา";
            case "1101":
                return "วินิจฉัย รักษา และตามความต้องการของผู้ป่วย";
            case "1110":
                return "วินิจฉัย รักษา และรับไว้รักษาต่อเนื่อง";
            case "1111":
                return "วินิจฉัย รักษา รับไว้รักษาต่อเนื่อง และตามความต้องการของผู้ป่วย";
            default:
                return "รหัสไม่ตรงกับที่กำหนด";
        }
    }

    // ตรวจสอบว่า UCAE เป็นค่าที่ถูกต้องหรือไม่
    private String getUCAE(String ucae) {
        switch (ucae) {
            case "A":
                return "Accident / Accident + Emergency";
            case "E":
                return "Emergency";
            case "NONE":
                return "ไม่เป็น A / E";
            case "I":
                return "OP Refer ในจังหวัด";
            case "O":
                return "OP Refer ข้ามจังหวัด";
            case "C":
                return "ย้ายหน่วยบริการเกิดสิทธิทันที";
            case "Z":
                return "บริการเชิงรุก";
            default:
                return "รหัสไม่ตรงกับที่กำหนด";
        }
    }

    // ตรวจสอบว่า EMTYPE เป็นค่าที่ถูกต้องหรือไม่
    private String getEMType(String emtype) {
        switch (emtype) {
            case "1":
                return "ต้องการรักษาเป็นการด่วน";
            case "2":
                return "ต้องผ่าตัดด่วน";
            case "3":
                return "โรคที่คณะกรรมการกำหนด";
            default:
                return "รหัสไม่ตรงกับที่กำหนด";
        }
    }
}
