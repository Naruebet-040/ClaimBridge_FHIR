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
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
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
public class DRUResource {

    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir";

    @PostMapping("/upload-dru")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty!";
        }

        List<MedicationRequest> medicationRequests = parseTextFile(file);
        return saveToFhirServer(medicationRequests);
    }

    private List<MedicationRequest> parseTextFile(MultipartFile file) {
        List<MedicationRequest> medicationRequests = new ArrayList<>();
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

                MedicationRequest medicationRequest = new MedicationRequest();
                medicationRequest.setId("medication-" + rowData.get("HN")); // ใช้ HN เป็น ID
                medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
                medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);

                // สร้าง CodeableConcept สำหรับ "DRU"
                CodeableConcept code = new CodeableConcept();
                code.setText("DRU");

                // กำหนดให้กับ medicationRequest
                medicationRequest.addCategory(code);

                // กำหนดรหัสสถานพยาบาล
                Identifier identifier = new Identifier();
                identifier.setSystem("https://terms.sil-th.org/hcode/");
                identifier.setValue(rowData.get("HCODE")); // HCODE
                medicationRequest.addIdentifier(identifier);

                // ref HN
                medicationRequest.setSubject(new Reference("Patient?identifier=" + rowData.get("HN")));

                // ref AN
                if (rowData.get("AN") != null) {
                    medicationRequest.setEncounter(new Reference("Encounter?identifier=" + rowData.get("AN")));
                }

                // ref Practitioner
                if (rowData.get("PROVIDER") != null) {
                    medicationRequest.setRequester(new Reference("Practitioner?identifier=" + rowData.get("PROVIDER")));
                }

                // CLINIC
                medicationRequest.addCategory(new CodeableConcept().addCoding(new Coding()
                        .setSystem("https://terms.sil-th.org/core/ValueSet/vs-eclaim-clinic")
                        .setCode(rowData.get("CLINIC"))// CLINIC
                        .setDisplay(getClinicDisplay(rowData.get("CLINIC")))));

                // กำหนดวันที่
                try {
                    SimpleDateFormat sdfInput = new SimpleDateFormat("yyyyMMdd", Locale.US);
                    Date dateServ = sdfInput.parse(rowData.get("DATE_SERV")); // DATE_SERV

                    // แปลงเป็นรูปแบบ dd-MM-yyyy
                    SimpleDateFormat sdfOutput = new SimpleDateFormat("yyyyMMdd", Locale.US);
                    String formattedDateStr = sdfOutput.format(dateServ);

                    // แปลงกลับเป็น Date
                    Date outputDate = sdfOutput.parse(formattedDateStr);

                    medicationRequest.setAuthoredOn(outputDate);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // ยา
                medicationRequest.setMedication(new CodeableConcept().addCoding(new Coding()
                        .setSystem("http://snomed.info/sct")
                        .setCode(rowData.get("DID")) // DID
                        .setDisplay(rowData.get("DIDNAME")) // DIDNAME
                ));

                // จำนวนที่จ่าย (AMOUNT)
                Extension amountExtension = new Extension();
                amountExtension.setValue(new StringType(rowData.get("AMOUNT"))); // AMOUNT
                medicationRequest.addExtension(amountExtension);

                // ราคาขายต่อหน่วย (DRUGPRICE)
                Extension drugPriceExtension = new Extension();
                drugPriceExtension.setValue(new StringType(rowData.get("DRUGPRICE"))); // DRUGPRICE
                medicationRequest.addExtension(drugPriceExtension);

                // ราคาทุน (DRUGCOST)
                Extension drugCostExtension = new Extension();
                drugCostExtension.setValue(new StringType(rowData.get("DRUGCOST"))); // DRUGCOST
                medicationRequest.addExtension(drugCostExtension);

                // หน่วย (UNIT)
                Extension unitExtension = new Extension();
                unitExtension.setValue(new StringType(rowData.get("UNIT"))); // UNIT
                medicationRequest.addExtension(unitExtension);

                // ขนาดบรรจุต่อหน่วย (UNIT_PACK)
                Extension unitPackExtension = new Extension();
                unitPackExtension.setValue(new StringType(rowData.get("UNIT_PACK"))); // UNIT_PACK
                medicationRequest.addExtension(unitPackExtension);

                // ยานอกบัญชียาหลัก (DRUGREMARK)
                if (!rowData.get("DRUGREMARK").isEmpty()) {
                    String drugremarkCode = rowData.get("DRUGREMARK");
                    Extension drugremarkExtension = new Extension();
                    drugremarkExtension
                            .setUrl("https://terms.sil-th.org/core/CodeSystem/cs-eclaim-medication-ned-criteria");
                    drugremarkExtension.setValue(new StringType(getDRUGREMARK(drugremarkCode)));
                    medicationRequest.addExtension(drugremarkExtension);
                }

                medicationRequests.add(medicationRequest);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return medicationRequests;
    }

    private long countMedicationRequestsByType(String claimTypeText) {
        try (MongoClient mongoClient = MongoClients.create(
                "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
            MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
            MongoCollection<Document> collection = database.getCollection("MedicationRequest File");

            // ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
            long count = collection.countDocuments(eq("category.text", "DRU"));
            return count;
        }
    }

    private String saveToFhirServer(List<MedicationRequest> medicationRequests) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);
        List<String> responseList = new ArrayList<>();
        // ดึงจำนวน ADP ที่มีอยู่ใน MongoDB
        long existingDRUCount = countMedicationRequestsByType("DRU");
        int skipped = 0;

        for (MedicationRequest medicationRequest : medicationRequests) {
            // ถ้ามี CHAR อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
            if (skipped < existingDRUCount) {
                skipped++;
                continue;
            }

            MethodOutcome outcome = client.create().resource(medicationRequest).execute();
            responseList.add("Created MedicationRequest ID: " + outcome.getId().getIdPart());

            // แปลง Patient เป็น JSON
            String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

            // บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
            saveToMongo(json);
        }
        if (skipped >= existingDRUCount && existingDRUCount > 0) {
            System.out.println("❌ (DRU) Skipping upload " + (skipped) + " record, already in system.");
        }

        return responseList.toString();
    }

    private void saveToMongo(String json) {
        // ตัวอย่างการบันทึกข้อมูล Patient ลง MongoDB
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("MedicationRequest File");

        // แปลง Patient เป็น JSON format ก่อนบันทึก
        Document document = Document.parse(json);

        // บันทึกข้อมูลลง MongoDB
        collection.insertOne(document);

        mongoClient.close();
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

    // ตรวจสอบว่า drugRemark อยู่ในกลุ่ม EU (ยานอกบัญชียาหลัก)
    private String getDRUGREMARK(String drugremarkExtension) {
        switch (drugremarkExtension) {
            case "EA":
                return "เกิดอาการไม่พึงประสงค์จากยาหรือแพ้ยาที่สามารถใช้ได้ในบัญชียาหลักแห่งชาติ";
            case "EB":
                return "ผลการรักษาไม่บรรลุเป้าหมายแม้ว่ามีได้ยาในบัญชียาหลักแห่งชาติตรงตามมาตรฐานการรักษา";
            case "EC":
                return "ไม่มียาอยู่ในบัญชียาหลักแห่งชาติที่ใช้ได้ แต่ผู้ป่วยมีความจำเป็นในการใช้ยา ตามข้อบ่งชี้ที่ได้รับการรับรอง";
            case "ED":
                return "ผู้ป่วยมีภาวะหรือโรคที่จำเป็นต้องใช้ยานอกบัญชียาหลักแห่งชาติในการรักษา และไม่มีวิธีอื่นที่ผู้ป่วยจำเป็นต้องใช้ยานอกบัญชียาหลักแห่งชาติ";
            case "EE":
                return "ยานอกบัญชียาหลักแห่งชาติที่มีราคาต่ำกว่า (ในเชิงความคุ้มค่า)";
            case "EF":
                return "ผู้ป่วยขอสงวนสิทธิ์ตามข้อกำหนด (เป็นไปได้)";
            case "PA":
                return "ถูกจำกัดสิทธิ์ของกลุ่มยาต้านมะเร็ง (PA) เช่น ยาเม็ด ยาฉีด ยารักษาภูมิคุ้มกันลดหรือโรคสะเก็ดเงิน 2 ชนิด";
            default:
                return "รหัสไม่ตรงกับที่กำหนด";
        }
    }
}