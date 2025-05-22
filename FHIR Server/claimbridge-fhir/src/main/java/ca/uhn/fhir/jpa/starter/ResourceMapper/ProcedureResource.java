package ca.uhn.fhir.jpa.starter.ResourceMapper;

import static com.mongodb.client.model.Filters.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
//import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
//import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Procedure;
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
public class ProcedureResource {
    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir"; // URL ของ HAPI FHIR Server

    @PostMapping("/upload-op")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty!";
        }

        List<Procedure> procedures = parseTextFile(file);
        return saveToFhirServer(procedures); // ส่งข้อมูลไปยัง FHIR Server
    }

    private List<Procedure> parseTextFile(MultipartFile file) {
        List<Procedure> procedures = new ArrayList<>();
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

                // สร้าง Procedure FHIR Resource
                Procedure procedure = new Procedure();

                // เพิ่ม Extension
                Extension procedureTypeExtension = new Extension();
                if (rowData.get("OPTYPE") != null) {
                    procedureTypeExtension.setUrl(
                            "https://fhir-ig.sil-th.org/th/extensions/StructureDefinition/ex-procedure-procedure-type");
                    procedureTypeExtension.setValue(new CodeableConcept().addCoding(new Coding()
                            .setSystem("https://terms.sil-th.org/core/ValueSet/vs-eclaim-procedure-type")
                            .setCode(rowData.get("OPTYPE"))// IOP: OPTYPE
                            .setDisplay(getProcedureType(rowData.get("OPTYPE")))));
                    procedure.addExtension(procedureTypeExtension);
                }

                procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
                if (rowData.get("HN") != null) {
                    // สร้าง CodeableConcept สำหรับ "OOP"
                    CodeableConcept code = new CodeableConcept();
                    code.setText("OOP");

                    // กำหนดให้กับประเภท (category) หรือ field อื่นๆ ของ Procedure
                    procedure.setCategory(code);

                    procedure.setSubject(new Reference("Patient?identifier=" + rowData.get("HN")));// OOP:
                    // HN
                }

                if (rowData.get("AN") != null) {
                    // สร้าง CodeableConcept สำหรับ "IOP"
                    CodeableConcept code = new CodeableConcept();
                    code.setText("IOP");

                    // กำหนดให้กับประเภท (category) หรือ field อื่นๆ ของ Procedure
                    procedure.setCategory(code);

                    procedure.setSubject(new Reference("Encounter?identifier=" + rowData.get("AN")));// IOP: AN
                }

                // // เพิ่ม HN Identifier
                // procedure.addIdentifier()
                // .setSystem("https://terms.sil-th.org/hcode/5/" + "99999" + "/HN")
                // .setValue(rowData.get("HN")) // OOP: HN
                // .setType(new CodeableConcept()
                // .addCoding(new Coding()
                // .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
                // .setCode("localHn")));

                // ตั้งค่า Code (ICD-9-CM)
                procedure.setCode(new CodeableConcept().addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/icd9cm")
                        .setCode(rowData.get("OPER")) // OOP: OPER, IOP: field[1]
                ));

                // วัน-เวลา
                SimpleDateFormat sdfDateOnly = new SimpleDateFormat("yyyyMMdd", Locale.US);
                SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyyMMdd HHmm", Locale.US);
                // ตั้งค่า PerformedPeriod
                Period performedPeriod = new Period();
                if (rowData.get("DATEOPD") != null) {
                    performedPeriod.setStartElement(new DateTimeType(sdfDateOnly.parse(rowData.get("DATEOPD"))));// OOP:DATEOPD
                }
                if (rowData.get("DATEIN") != null) {
                    Date startDate = sdfDateTime.parse(rowData.get("DATEIN") + " " + rowData.get("TIMEIN")); // DATEIN +
                                                                                                             // TIMEIN
                    performedPeriod.setStartElement(new DateTimeType(startDate));// IOP:DATEIN
                }
                if (rowData.get("DATEOUT") != null) {
                    Date endDate = sdfDateTime.parse(rowData.get("DATEOUT") + " " + rowData.get("TIMEOUT")); // DATEOUT
                                                                                                             // +
                                                                                                             // TIMEOUT

                    performedPeriod.setEndElement(new DateTimeType(endDate));// IOP:DATEOUT
                }
                procedure.setPerformed(performedPeriod);

                if (rowData.get("DROPID") != null) {
                    // ตั้งค่า Performer
                    procedure.addPerformer()
                            .setActor(new Reference("Practitioner?identifier=" + rowData.get("DROPID")));// OOP:
                                                                                                         // DROPID,
                                                                                                         // IOP:
                                                                                                         // field[3]
                }

                // กำหนดประเภทของการรักษา (e.g., อายุรกรรม)
                if (rowData.get("CLINIC") != null) {
                    procedure.addReasonCode(new CodeableConcept().addCoding(new Coding()
                            .setSystem("https://terms.sil-th.org/core/ValueSet/vs-eclaim-clinic")
                            .setCode(rowData.get("CLINIC"))// OOP:CLINIC
                            .setDisplay(getClinicDisplay(rowData.get("CLINIC")))));
                }

                procedures.add(procedure);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return procedures;
    }

    private long countIOPProceduresByType(String procedureTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("Procedure File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("category.text", "IOP"));
            return count;
        }
    }

    private long countOOPProceduresByType(String procedureTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("Procedure File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("category.text", "OOP"));
            return count;
        }
    }

    private String saveToFhirServer(List<Procedure> procedures) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

        List<String> responseList = new ArrayList<>();
        long existingIOPCount = countIOPProceduresByType("IOP");
        long existingOOPCount = countOOPProceduresByType("OOP");

        int iop_skipped = 0;
        int oop_skipped = 0;

        for (Procedure procedure : procedures) {
            try {
                if (procedure instanceof Procedure) {
                    Procedure procedureResource = (Procedure) procedure;

                    // เข้าถึงข้อความของ category
                    CodeableConcept code = procedureResource.getCategory(); // สมมุติว่า category เป็น List

                    if (code.getText().equals("IOP")) {
                        // ถ้ามี IRF อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
                        if (iop_skipped < existingIOPCount) {
                            iop_skipped++;
                            continue;
                        }
                    } else if (code.getText().equals("OOP")) {
                        // ถ้ามี ORF อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
                        if (oop_skipped < existingOOPCount) {
                            oop_skipped++;
                            continue;
                        }
                    }
                }

                MethodOutcome outcome = client.create().resource(procedure).execute();
                // ตรวจสอบว่า FHIR Server ส่งกลับอะไร
                if (outcome.getCreated()) {
                    responseList.add("Uploaded Procedure: " + outcome.getId().getIdPart());
                } else {
                    responseList.add("Failed to upload Procedure: " + procedure.getId());
                }

                // แปลง Patient เป็น JSON
                String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

                // บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
                saveToMongo(json);

            } catch (Exception e) {
                responseList.add("Error uploading Procedure: " + procedure.getId() + " - " + e.getMessage());
                e.printStackTrace(); // พิมพ์ stack trace เพื่อ debug
            }
        }

        if (iop_skipped >= existingIOPCount && existingIOPCount > 0) {
            System.out.println("❌ (IOP) Skipping upload " + (iop_skipped) + " record, already in system.");
        }
        if (oop_skipped >= existingOOPCount && existingOOPCount > 0) {
            System.out.println("❌ (OOP) Skipping upload " + (oop_skipped) + " record, already in system.");
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

    private String getProcedureType(String proceduretype) {
        switch (proceduretype) {
            case "1":
                return "Principal procedure";
            case "2":
                return "Secondary procedure";
            case "3":
                return "Other";
            default:
                return "ไม่พบข้อมูล";
        }
    }

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

}
