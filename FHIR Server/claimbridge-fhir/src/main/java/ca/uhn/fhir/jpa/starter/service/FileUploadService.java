package ca.uhn.fhir.jpa.starter.service;

import java.io.File;
import java.nio.file.Files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FileUploadService {

    private final RestTemplate restTemplate; // ✅ ไม่สร้างเอง แต่ Inject จากข้างนอกเข้ามา

    // 👉 เพิ่ม Constructor นี้
    public FileUploadService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // 👉 และสำหรับ Spring Boot ตอนรันปกติ ก็มี default constructor แบบนี้
    public FileUploadService() {
        this.restTemplate = new RestTemplate();
    }

    @Value("${file.upload-path}/${file.dataset-name}/PAT.txt") // path ของไฟล์ Patient.txt "${file.patient-path}"
    private String patientFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/OPD.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String opdFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/IPD.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String ipdFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/IRF.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String irfFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/ORF.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String orfFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/INS.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String insFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/ADP.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String adpFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/CHT.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String chtFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/CHA.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String chaFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/IDX.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String idxFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/ODX.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String odxFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/OOP.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String oopFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/IOP.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String iopFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/LABFU.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String labfuFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/LVD.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String lvdFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/AER.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String aerFilePath;

    @Value("${file.upload-path}/${file.dataset-name}/DRU.txt") // path ของไฟล์ OPD.txt "${file.opd-path}"
    private String druFilePath;

    // Starter Json File
    @Value("${file.upload-path}/pre_jsonfile/Organization.json")
    private String organizationPath;

    @Value("${file.upload-path}/pre_jsonfile/Practitioner.json")
    private String practitionerPath;

    @Value("${file.upload-path}/pre_jsonfile/Coverage.json")
    private String coveragePath;

    // private final RestTemplate restTemplate = new RestTemplate();

    // 📌 ✅ โหลด Organization.json และอัปโหลดก่อนเริ่ม Scheduled Task
    @EventListener(ApplicationReadyEvent.class)
    public void uploadInitialFiles() {
        System.out.println("🚀 Start the initial file upload...");
        uploadJsonFile("http://localhost:8080/fhir/", organizationPath);
        uploadJsonFile("http://localhost:8080/fhir/", practitionerPath);
        uploadJsonFile("http://localhost:8080/fhir/Coverage", coveragePath);
        System.out.println("✅ Initial file upload successful");
    }

    @Scheduled(fixedRate = 12 * 60 * 60 * 1000) // รันทุก 12 ชั่วโมง 12 * 60 *
    public void uploadFiles() {
        System.out.println("🔄 Starting file upload...");
        String Root_Path = "http://localhost:8080/api/fhir/";

        uploadFile(Root_Path + "upload-pat", patientFilePath); // PAT

        uploadFile(Root_Path + "upload-opd", opdFilePath); // OPD
        uploadFile(Root_Path + "upload-ipd", ipdFilePath); // IPD

        uploadFile(Root_Path + "upload-rf", irfFilePath); // IRF
        uploadFile(Root_Path + "upload-rf", orfFilePath); // ORF

        uploadFile(Root_Path + "upload-ins", insFilePath); // INS
        uploadFile(Root_Path + "upload-adp", adpFilePath); // ADP
        uploadFile(Root_Path + "upload-chad", chtFilePath); // CHT
        uploadFile(Root_Path + "upload-char", chaFilePath); // CHA
        uploadFile(Root_Path + "upload-iodx", idxFilePath); // IDX
        uploadFile(Root_Path + "upload-iodx", odxFilePath); // ODX

        uploadFile(Root_Path + "upload-op", oopFilePath); // OOP
        uploadFile(Root_Path + "upload-op", iopFilePath); // IOP

        uploadFile(Root_Path + "upload-labfu", labfuFilePath); // LABFU
        uploadFile(Root_Path + "upload-lvd", lvdFilePath); // LVD
        uploadFile(Root_Path + "upload-aer", aerFilePath); // AER
        uploadFile(Root_Path + "upload-dru", druFilePath); // DRU

        System.out.println("✅ File uploaded successfully");
    }

    protected void uploadFile(String url, String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("❌ File not found: " + filePath);
                return;
            }

            // อ่านไฟล์และแปลงเป็น byte[]
            byte[] fileContent = Files.readAllBytes(file.toPath());

            // สร้าง Multipart Request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("file", fileContent)
                    .header("Content-Disposition", "form-data; name=file; filename=" + file.getName());

            HttpEntity<?> requestEntity = new HttpEntity<>(bodyBuilder.build(), headers);

            // ส่งไฟล์ไปยัง API
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            System.out.println("📤  Uploading file: " + filePath + " -> " + response.getStatusCode());

        } catch (Exception e) {
            System.out.println("❌ An error occurred during the upload: " + e.getMessage());
        }

    }

    public void uploadJsonFile(String url, String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("❌ JSON file not found: " + filePath);
                return;
            }

            // อ่านไฟล์ JSON และแปลงเป็น String
            String jsonContent = new String(Files.readAllBytes(file.toPath()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> requestEntity = new HttpEntity<>(jsonContent, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            System.out.println("📤 Upload JSON file: " + filePath + " -> " + response.getStatusCode());
        } catch (Exception e) {
            System.out.println("❌ An error occurred while uploading the JSON: " + e.getMessage());
        }
    }

}
