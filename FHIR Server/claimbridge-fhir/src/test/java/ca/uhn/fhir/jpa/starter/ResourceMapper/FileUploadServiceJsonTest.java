package ca.uhn.fhir.jpa.starter.ResourceMapper;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import ca.uhn.fhir.jpa.starter.service.FileUploadService;

class FileUploadServiceTest {

    @Test
    void testUploadJsonFile() throws Exception {
        // Mock RestTemplate
        RestTemplate mockRestTemplate = mock(RestTemplate.class);
        when(mockRestTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // สร้างไฟล์ทดสอบ (Mock)
        File tempFile = File.createTempFile("test", ".json");
        Files.write(tempFile.toPath(), "{\"key\":\"value\"}".getBytes());

        // ใช้ Constructor ใหม่ ที่รับ RestTemplate
        FileUploadService service = new FileUploadService(mockRestTemplate);

        // เรียก function uploadJsonFile
        service.uploadJsonFile("http://localhost:8080/fhir/", tempFile.getAbsolutePath());

        // ตรวจสอบว่าเรียก exchange 1 ครั้ง
        verify(mockRestTemplate, times(1)).exchange(anyString(), any(), any(), eq(String.class));

        tempFile.delete(); // ลบไฟล์ทิ้งหลังทดสอบ
    }
}
