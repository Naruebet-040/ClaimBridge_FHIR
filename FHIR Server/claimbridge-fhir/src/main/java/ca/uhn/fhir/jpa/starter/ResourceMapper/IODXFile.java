package ca.uhn.fhir.jpa.starter.ResourceMapper;

import static com.mongodb.client.model.Filters.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
//import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
// import org.hl7.fhir.r4.model.Patient;
// import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
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
public class IODXFile {
    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir"; // URL ของ HAPI FHIR Server
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

    @PostMapping("/upload-iodx")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty!";
        }

        List<Condition> conditions = parseTextFile(file);
        return saveToFhirServer(conditions); // ส่งข้อมูลไปยัง FHIR Server
    }

    private List<Condition> parseTextFile(MultipartFile file) {
        List<Condition> conditions = new ArrayList<>();
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

                // ดึงค่า HCODE อัตโนมัติจาก Patient ID
                // String hcode = getHcodeFromPatient();

                // สร้าง Condition FHIR Resource
                Condition condition = new Condition();

                if (rowData.get("DATEDX") != null) {
                    condition.setRecordedDate(dateFormat.parse(rowData.get("DATEDX"))); // DATEDX
                }

                condition.setAsserter(new Reference("Practitioner?identifier=" + rowData.get("DRDX")));// DRDX

                if (rowData.get("HN") != null) {
                    // สร้าง CodeableConcept สำหรับ "ODX"
                    CodeableConcept code = new CodeableConcept();
                    code.setText("ODX");

                    // กำหนดให้กับประเภท (category) หรือ field อื่นๆ ของ ServiceRequest
                    condition.addCategory(code); // หรือใช้ field อื่นๆ เช่น reasonCode ตามที่เหมาะสม

                    condition.setSubject(new Reference("Patient?identifier=" + rowData.get("HN")));// ODX: HN
                }

                if (rowData.get("AN") != null) {
                    // สร้าง CodeableConcept สำหรับ "ODX"
                    CodeableConcept code = new CodeableConcept();
                    code.setText("IDX");

                    // กำหนดให้กับประเภท (category) หรือ field อื่นๆ ของ ServiceRequest
                    condition.addCategory(code); // หรือใช้ field อื่นๆ เช่น reasonCode ตามที่เหมาะสม

                    condition.setSubject(new Reference("Encounter?identifier=" + rowData.get("AN")));// IDX: AN
                }

                // **หมายเลขประจำตัวประชาชน**
                if (rowData.get("PERSON_ID") != null) {
                    condition.addIdentifier()
                            .setType(new CodeableConcept().addCoding(new Coding()
                                    .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
                                    .setCode("cid")
                                    .setDisplay("เลขประจำตัวประชาชนไทย")))
                            .setSystem("https://terms.sil-th.org/id/th-cid")
                            .setValue(rowData.get("PERSON_ID"));// PERSON_ID
                }

                // ICD-10 Code
                condition.setCode(new CodeableConcept().addCoding(new Coding()
                        .setSystem("http://hl7.org/fhir/sid/icd-10")
                        .setCode(rowData.get("DIAG"))// DIAG
                ));

                // Clinic
                if (rowData.get("CLINIC") != null) {
                    condition.addCategory(new CodeableConcept().addCoding(new Coding()
                            .setSystem("https://terms.sil-th.org/core/ValueSet/vs-eclaim-clinic")
                            .setCode(rowData.get("CLINIC"))// CLINIC
                            .setDisplay(getClinicDisplay(rowData.get("CLINIC")))));
                }

                // DIAGTYPE or DXTYPE
                condition.addCategory(new CodeableConcept().addCoding(new Coding()
                        .setSystem("https://terms.sil-th.org/core/ValueSet/vs-43plus-encounter-diagnosis-role")
                        .setCode(rowData.get("DXTYPE"))// DXTYPE
                        .setDisplay(getDIAGTYPEDisplay(rowData.get("DXTYPE")))));

                conditions.add(condition);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return conditions;
    }

    private long countIDXConditionsByType(String conditionTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("Condition File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("category.text", "IDX"));
            return count;
        }
    }

    private long countODXConditionsByType(String conditionTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("Condition File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("category.text", "ODX"));
            return count;
        }
    }

    private String saveToFhirServer(List<Condition> conditions) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

        List<String> responseList = new ArrayList<>();
        long existingIDXCount = countIDXConditionsByType("IDX");
        long existingODXCount = countODXConditionsByType("ODX");

        int idx_skipped = 0;
        int odx_skipped = 0;

        for (Condition condition : conditions) {
            try {
                if (condition instanceof Condition) {
                    Condition conditionResource = (Condition) condition;

                    // เข้าถึงข้อความของ category
                    CodeableConcept code = conditionResource.getCategory().get(0); // สมมุติว่า category เป็น List

                    if (code.getText().equals("IDX")) {
                        // ถ้ามี IRF อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
                        if (idx_skipped < existingIDXCount) {
                            idx_skipped++;
                            continue;
                        }
                    } else if (code.getText().equals("ODX")) {
                        // ถ้ามี ORF อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
                        if (odx_skipped < existingODXCount) {
                            odx_skipped++;
                            continue;
                        }
                    }

                }

                MethodOutcome outcome = client.create().resource(condition).execute();
                // ตรวจสอบว่า FHIR Server ส่งกลับอะไร
                if (outcome.getCreated()) {
                    responseList.add("Uploaded condition Condition: " + outcome.getId().getIdPart());
                } else {
                    responseList.add("Failed to upload condition Condition: " + condition.getId());
                }

                // แปลง Patient เป็น JSON
                String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

                // บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
                saveToMongo(json);

            } catch (Exception e) {
                responseList.add("Error uploading Condition: " + condition.getId() + " - " + e.getMessage());
                e.printStackTrace(); // พิมพ์ stack trace เพื่อ debug
            }
        }

        if (idx_skipped >= existingIDXCount && existingIDXCount > 0) {
            System.out.println("❌ (IDX) Skipping upload " + (idx_skipped) + " record, already in system.");
        }
        if (odx_skipped >= existingODXCount && existingODXCount > 0) {
            System.out.println("❌ (ODX) Skipping upload " + (odx_skipped) + " record, already in system.");
        }

        return responseList.toString();
    }

    private void saveToMongo(String json) {
        // ตัวอย่างการบันทึกข้อมูล Patient ลง MongoDB
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("Condition File");

        // แปลง Patient เป็น JSON format ก่อนบันทึก
        Document document = Document.parse(json);

        // บันทึกข้อมูลลง MongoDB
        collection.insertOne(document);

        mongoClient.close();
    }

    // private String getHcodeFromPatient(String personId) {
    // // ลิงก์ Patient ID กับ HCODE
    // String hcode = "99999"; // ค่า default หากหาไม่เจอ
    // try {
    // FhirContext ctx = FhirContext.forR4();
    // IGenericClient client =
    // ctx.newRestfulGenericClient("http://localhost:8080/fhir");

    // Bundle bundle = client.search()
    // .forResource(Patient.class)
    // .where(Patient.IDENTIFIER.exactly().identifier(personId))
    // .returnBundle(Bundle.class)
    // .execute();

    // if (!bundle.getEntry().isEmpty()) {
    // Patient patient = (Patient) bundle.getEntryFirstRep().getResource();
    // hcode =
    // patient.getManagingOrganization().getReference().replace("Organization/",
    // "");
    // }
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // return hcode;
    // }

    private String getClinicDisplay(String clinic) {
        switch (clinic) {
            case "00":
                return "หน่วยงานระดับสถานีอนามัยและศูนย์สุขภาพชุมชน";
            case "01":
                return "อายุรกรรม";
            case "02":
                return "ศัลยกรรม";
            case "03":
                return "สูติกรรม";
            case "04":
                return "นรีเวชกรรม";
            case "05":
                return "กุมารเวชกรรม";
            case "06":
                return "โสต ศอ นาสิก";
            case "07":
                return "จักษุวิทยา";
            case "08":
                return "ศัลยกรรมออร์โธปิดิกส์";
            case "09":
                return "จิตเวช";
            case "10":
                return "รังสีวิทยา";
            case "11":
                return "ทันตกรรม";
            case "12":
                return "เวชศาสตร์ฉุกเฉินและนิติเวช";
            case "13":
                return "เวชกรรมฟื้นฟู";
            case "14":
                return "แพทย์แผนไทย";
            case "15":
                return "PCU ในรพ.";
            case "16":
                return "เวชกรรมปฏิบัติทั่วไป";
            case "17":
                return "เวชศาสตร์ครอบครัวและชุมชน";
            case "18":
                return "อาชีวคลินิก";
            case "19":
                return "วิสัญญีวิทยา (คลินิกระงับปวด)";
            case "20":
                return "ศัลยกรรมประสาท";
            case "21":
                return "อาชีวเวชกรรม";
            case "22":
                return "เวชกรรมสังคม";
            case "23":
                return "พยาธิวิทยากายวิภาค";
            case "24":
                return "พยาธิวิทยาคลินิก";
            case "25":
                return "แพทย์ทางเลือก";
            case "99":
                return "อื่น ๆ";
            default:
                return "ไม่พบข้อมูล";
        }
    }

    private String getDIAGTYPEDisplay(String diagType) {
        switch (diagType) {
            case "1":
                return "PRINCIPLE DX (การวินิจฉัยโรคหลัก)";
            case "2":
                return "CO-MORBIDITY (การวินิจฉัยโรคร่วม)";
            case "3":
                return "COMPLICATION (การวินิจฉัยโรคแทรก)";
            case "4":
                return "OTHER (อื่น ๆ)";
            case "5":
                return "EXTERNAL CAUSE (สาเหตุภายนอก)";
            case "6":
                return "Additional Code (รหัสเสริม)";
            case "7":
                return "Morphology Code (รหัสเกี่ยวกับเนื้องอก)";
            default:
                return "ไม่พบข้อมูล";
        }
    }
}
