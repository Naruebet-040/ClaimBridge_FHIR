package ca.uhn.fhir.jpa.starter.ResourceMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
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
public class PatientResource {

    private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir"; // URL ของ HAPI FHIR Server
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");

    @PostMapping("/upload-pat")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return "File is empty!";
        }

        List<Patient> patients = parseTextFile(file);
        return saveToFhirServer(patients); // ส่งข้อมูลไปยัง FHIR Server
    }

    private List<Patient> parseTextFile(MultipartFile file) {
        List<Patient> patients = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false; // ข้าม Header บรรทัดแรก
                    continue;
                }

                String[] data = line.split("\\|");
                if (data.length < 15)
                    continue; // ตรวจสอบจำนวนฟิลด์

                Patient patient = new Patient();

                // 🔹 ชื่อ-นามสกุล
                patient.addName().setFamily(data[13]) // LNAME
                        .addGiven(data[12]); // FNAME

                // 🔹 วันเกิด
                try {
                    patient.setBirthDate(dateFormat.parse(data[4])); // DOB
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                // 🔹 เพศ (2 = Male, 1 = Female)
                if ("2".equals(data[5])) {
                    patient.setGender(AdministrativeGender.MALE);
                } else if ("1".equals(data[5])) {
                    patient.setGender(AdministrativeGender.FEMALE);
                } else {
                    patient.setGender(AdministrativeGender.UNKNOWN);
                }

                // 🔹 หมายเลข HN
                patient.addIdentifier()
                        .setSystem("https://terms.sil-th.org/hcode/5/" + data[0] + "/HN")
                        .setValue(data[1]) // HN
                        .setType(new CodeableConcept()
                                .addCoding(new Coding()
                                        .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
                                        .setCode("localHn")));

                // 🔹 สถานภาพสมรส (MARRIAGE)
                String marriageStatus = data[6]; // สมรสจากไฟล์
                CodeableConcept maritalStatus = new CodeableConcept();

                if ("1".equals(marriageStatus)) {
                    maritalStatus.addCoding(new Coding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus")
                            .setCode("M")
                            .setDisplay("Married"));
                    maritalStatus.addCoding(new Coding()
                            .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-thcc-marital")
                            .setCode("1")
                            .setDisplay("แต่งงาน"));
                } else if ("6".equals(marriageStatus)) {
                    maritalStatus.addCoding(new Coding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus")
                            .setCode("S")
                            .setDisplay("Separated"));
                    maritalStatus.addCoding(new Coding()
                            .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-thcc-marital")
                            .setCode("6")
                            .setDisplay("แยกกันอยู่"));
                } else if ("9".equals(marriageStatus)) {
                    maritalStatus.addCoding(new Coding()
                            .setSystem("http://terminology.hl7.org/CodeSystem/v3-MaritalStatus")
                            .setCode("U")
                            .setDisplay("Unknown"));
                    maritalStatus.addCoding(new Coding()
                            .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-thcc-marital")
                            .setCode("9")
                            .setDisplay("ไม่ระบุ"));
                }

                patient.setMaritalStatus(maritalStatus);

                // 🔹 หมายเลขประจำตัวประชาชน พร้อม type
                patient.addIdentifier()
                        .setType(new CodeableConcept().addCoding(new Coding()
                                .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
                                .setCode("cid")
                                .setDisplay("เลขประจำตัวประชาชนไทย")))
                        .setSystem("https://terms.sil-th.org/id/th-cid")
                        .setValue(data[9]);

                // 🔹 ที่อยู่จังหวัด (CHANGWAT)
                Address address = new Address();

                address.setCity(getProvinceName(data[2]));

                // Extension สำหรับ province
                Extension provinceExtension = new Extension();
                provinceExtension.setUrl("province");
                provinceExtension.setValue(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-dopa-location")
                                .setCode(data[2]))); // Province code

                // Extension สำหรับ district
                Extension districtExtension = new Extension();
                districtExtension.setUrl("district");
                districtExtension.setValue(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-dopa-location")
                                .setCode(data[2] + data[3]))); // District code

                // Extension กลุ่ม address
                Extension addressExtension = new Extension();
                addressExtension
                        .setUrl("https://fhir-ig.sil-th.org/th/extensions/StructureDefinition/ex-address-address-code");
                addressExtension.addExtension(provinceExtension);
                addressExtension.addExtension(districtExtension);

                address.addExtension(addressExtension);

                patient.addAddress(address);

                // patient.setAddress(List.of(new org.hl7.fhir.r4.model.Address()
                // .setCity(getProvinceName(data[2]))));

                // 🔹 เพิ่ม Extension สำหรับสัญชาติ (Nationality)
                Extension nationalityExtension = new Extension();
                nationalityExtension.setUrl(
                        "https://fhir-ig.sil-th.org/th/extensions/StructureDefinition/ex-th-patient-nationality");
                Extension codeExtension = new Extension();
                codeExtension.setUrl("code");
                codeExtension.setValue(new CodeableConcept()
                        .addCoding(new Coding()
                                .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-thcc-nationality")
                                .setCode(data[8]))); // NATION (รหัสสัญชาติ)

                nationalityExtension.addExtension(codeExtension);
                patient.addExtension(nationalityExtension);

                patients.add(patient);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return patients;
    }

    // ฟังก์ชันใหม่ในการตรวจสอบข้อมูลซ้ำ
    private boolean isDuplicatePatient(String patientId) {
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("Patient File");

        // ค้นหาข้อมูล Patient ใน MongoDB โดยใช้รหัสประจำตัว (HN หรือ CID)
        Document existingDocument = collection.find(new Document("identifier.value", patientId)).first();

        mongoClient.close();

        return existingDocument != null;
    }

    private String saveToFhirServer(List<Patient> patients) {
        FhirContext ctx = FhirContext.forR4();
        IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

        List<String> responseList = new ArrayList<>();
        for (Patient patient : patients) {

            // ตรวจสอบว่า patient มีอยู่ใน MongoDB หรือไม่
            String patientId = patient.getIdentifierFirstRep().getValue(); // ใช้ HN หรือ CID เป็นตัวระบุ
            if (isDuplicatePatient(patientId)) {
                System.out.println("❌ (PAT) HN: " + patientId + " is already duplicated in the system!");
                continue; // ข้ามการอัปโหลดและการบันทึก
            }
            MethodOutcome outcome = client.create().resource(patient).execute();
            responseList.add("Created Patient ID: " + outcome.getId().getIdPart());

            // แปลง Patient เป็น JSON
            String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

            // บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
            saveToMongo(json);
        }
        return responseList.toString();
    }

    private void saveToMongo(String json) {
        // ตัวอย่างการบันทึกข้อมูล Patient ลง MongoDB
        MongoClient mongoClient = MongoClients
                .create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
        MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
        MongoCollection<Document> collection = database.getCollection("Patient File");

        // แปลง Patient เป็น JSON format ก่อนบันทึก
        Document document = Document.parse(json);

        // บันทึกข้อมูลลง MongoDB
        collection.insertOne(document);

        mongoClient.close();
    }

    // 🔹 ฟังก์ชันแปลงรหัสจังหวัดเป็นชื่อ
    private String getProvinceName(String provinceCode) {
        return switch (provinceCode) {
            case "11" -> "กรุงเทพมหานคร";
            case "12" -> "สมุทรปราการ";
            case "73" -> "ราชบุรี";
            case "75" -> "สมุทรสงคราม";
            case "83" -> "ภูเก็ต";
            case "84" -> "สุราษฎร์ธานี";
            default -> "ไม่ทราบจังหวัด";
        };
    }
}
