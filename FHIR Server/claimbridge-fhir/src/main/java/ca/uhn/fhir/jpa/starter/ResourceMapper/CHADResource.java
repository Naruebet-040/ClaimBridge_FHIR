package ca.uhn.fhir.jpa.starter.ResourceMapper;

import static com.mongodb.client.model.Filters.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
//import java.util.UUID;
import java.util.Map;

import org.bson.Document;
//import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
// import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
//import org.hl7.fhir.r4.model.Patient;
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
public class CHADResource {
	private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir"; // URL ของ HAPI FHIR Server
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

	@PostMapping("/upload-chad")
	public String uploadFile(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			return "File is empty!";
		}

		List<Claim> chads = parseTextFile(file);
		return saveToFhirServer(chads); // ส่งข้อมูลไปยัง FHIR Server
	}

	private List<Claim> parseTextFile(MultipartFile file) {
		List<Claim> chads = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
			String line;
			int sequence = 1; // เริ่มจาก sequence 1

			// อ่าน Header
			String[] headers = br.readLine().split("\\|", -1);

			while ((line = br.readLine()) != null) {
				String[] data = line.split("\\|", -1);
				if (data.length < headers.length)
					continue;

				// Map เก็บชื่อคอลัมน์ -> ค่าข้อมูล
				Map<String, String> rowData = new HashMap<>();
				for (int i = 0; i < headers.length; i++) {
					rowData.put(headers[i], data[i]);
				}

				// สร้าง Claim FHIR Resource
				Claim chad = new Claim();

				CodeableConcept type = new CodeableConcept();
				type.setText("CHAD"); // ตั้งข้อความตรง ๆ โดยไม่ต้องใช้ Coding
				chad.setType(type);

				// ----- เพิ่ม Extension ----- //
				Extension projectcode = new Extension();
				projectcode.setUrl("https://fhir-ig.sil-th.org/th/extensions/StructureDefinition/ex-chi-project-code");
				projectcode.setValue(new StringType("HOSPIC"));

				chad.addExtension(projectcode);

				// ตั้งค่า Status
				chad.setStatus(Claim.ClaimStatus.ACTIVE);
				chad.setCreated(dateFormat.parse(rowData.get("DATE"))); // CHT: DATE
				chad.setPatient(new Reference("Patient?identifier=" + rowData.get("HN"))); // CHT: HN

				// เพิ่ม HN Identifier
				chad.addIdentifier()
						.setSystem("https://terms.sil-th.org/hcode/5/" + "99999" + "/HN")
						.setValue(rowData.get("HN")) // CHT: HN
						.setType(new CodeableConcept()
								.addCoding(new Coding()
										.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
										.setCode("localHn")));

				// เพิ่ม AN Identifier (ถ้ามีค่า)
				if (!rowData.get("AN").isEmpty()) {
					chad.addIdentifier()
							.setSystem("https://terms.sil-th.org/hcode/5/" + "99999" + "/AN")
							.setValue(rowData.get("AN")) // CHT: AN
							.setType(new CodeableConcept()
									.addCoding(new Coding()
											.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
											.setCode("localAn")));
				}

				// เพิ่ม Identifier InvNo
				chad.addIdentifier()
						.setSystem("https://terms.sil-th.org/hcode/5/" + "99999" + "/Inv")
						.setValue(rowData.get("INVOICE_NO")) // CHT: INVOICE_NO
						.setType(new CodeableConcept()
								.addCoding(new Coding()
										.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
										.setCode("localInvNo")));

				// เพิ่ม Identifier InvLt ถ้ามี
				if (!rowData.get("INVOICE_LT").isEmpty()) {
					chad.addIdentifier()
							.setSystem("https://terms.sil-th.org/hcode/5/" + "99999" + "/InvLt")
							.setValue(rowData.get("INVOICE_LT")) // CHT: InvLt
							.setType(new CodeableConcept()
									.addCoding(new Coding()
											.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
											.setCode("localInvLt")));
				}

				chad.getTotal().setValue(Double.parseDouble(rowData.get("TOTAL"))).setCurrency("THB"); // CHT: TOTAL

				// เพิ่ม supportingInfo
				Claim.SupportingInformationComponent supportingInfo = new Claim.SupportingInformationComponent();
				supportingInfo.setSequence(sequence); // ตั้งค่าลำดับจากตัวแปร sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				// supportingInfo.setCode(new CodeableConcept()
				// .addCoding(new Coding()
				// .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-eclaim-medication-ned-criteria")
				// .setCode("EA")
				// .setDisplay("เกิดอาการไม่พึงประสงค์จากยาหรือแพ้ยาที่สามารถใช้ได้ในบัญชียาหลักแห่งชาติ")
				// ));
				if (!rowData.get("OPD_MEMO").isEmpty()) {
					supportingInfo.setCategory(new CodeableConcept()
							.addCoding(new Coding()
									.setSystem("http://example.org/custom-codes")
									.setCode(rowData.get("OPD_MEMO")) // OPD_MEMO
									.setDisplay("OPD MEMO")));
				}

				chad.addSupportingInfo(supportingInfo);
				chads.add(chad);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return chads;
	}

	private long countClaimsByType(String claimTypeText) {
		try (MongoClient mongoClient = MongoClients.create(
				"mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
			MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
			MongoCollection<Document> collection = database.getCollection("Claim File");

			// ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
			long count = collection.countDocuments(eq("type.text", "CHAD"));
			return count;
		}
	}

	private String saveToFhirServer(List<Claim> chads) {
		FhirContext ctx = FhirContext.forR4();
		IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

		List<String> responseList = new ArrayList<>();
		// ดึงจำนวน ADP ที่มีอยู่ใน MongoDB
		long existingCHADCount = countClaimsByType("CHAD");
		int skipped = 0;

		for (Claim chad : chads) {
			try {

				// ถ้ามี CHAD อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
				if (skipped < existingCHADCount) {
					skipped++;
					continue;
				}

				MethodOutcome outcome = client.create().resource(chad).execute();
				// ตรวจสอบว่า FHIR Server ส่งกลับอะไร
				if (outcome.getCreated()) {
					responseList.add("Uploaded CHAD Claim: " + outcome.getId().getIdPart());
				} else {
					responseList.add("Failed to upload CHAD Claim: " + chad.getId());
				}

				// แปลง Patient เป็น JSON
				String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

				// บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
				saveToMongo(json);

			} catch (Exception e) {
				responseList.add("Error uploading Claim: " + chad.getId() + " - " + e.getMessage());
				e.printStackTrace(); // พิมพ์ stack trace เพื่อ debug
			}
		}

		if (skipped >= existingCHADCount && existingCHADCount > 0) {
			System.out.println("❌ (CHAD) Skipping upload " + (skipped) + " record, already in system.");
		}

		return responseList.toString();
	}

	private void saveToMongo(String json) {
		// ตัวอย่างการบันทึกข้อมูล Patient ลง MongoDB
		MongoClient mongoClient = MongoClients
				.create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
		MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
		MongoCollection<Document> collection = database.getCollection("Claim File");

		// แปลง Patient เป็น JSON format ก่อนบันทึก
		Document document = Document.parse(json);

		// บันทึกข้อมูลลง MongoDB
		collection.insertOne(document);

		mongoClient.close();
	}

	// private String getMedNedCriteria (String code) {
	// switch (code) {
	// case "EA":
	// return
	// "เกิดอาการไม่พึงประสงค์จากยาหรือแพ้ยาที่สามารถใช้ได้ในบัญชียาหลักแห่งชาติ";
	// case "EB":
	// return
	// "ผลการรักษาไม่บรรลุเป้าหมายแม้ว่าได้ใช้ยาในบัญชียาหลักแห่งชาติครบตามมาตรฐานการรักษาแล้ว";
	// case "EC":
	// return "ไม่มีกลุ่มยาในบัญชียาหลักแห่งชาติให้ใช้
	// แต่ผู้ป่วยมีความจำเป็นในการใช้ยานี้
	// ตามข้อบ่งชี้ที่ได้ขึ้นทะเบียนไว้กับสำนักงานคณะกรรมการอาหารและยา";
	// case "ED":
	// return "ผู้ป่วยมีภาวะหรือโรคที่ห้ามใช้ยาในบัญชีอย่างสมบูรณ์
	// หรือมีข้อห้ามการใช้ยาในบัญชีร่วมกับยาอื่น
	// ที่ผู้ป่วยจำเป็นต้องใช้อย่างหลักเลี่ยงไม่ได้";
	// case "EE":
	// return "ยาในบัญชียาหลักแห่งชาติมีราคาแพงกว่า (ในเชิงความคุ้มค่า)";
	// case "EF":
	// return "ผู้ป่วยแสดงความจำนงต้องการ (เบิกไม่ได้)";
	// case "PA":
	// return "ยากลุ่มที่ต้องขออนุมัติก่อนการใช้ (PA) เช่น ยามะเร็ง 6 ชนิด
	// ยารักษากลุ่มโรครูมาติกและโรคสะเก็ดเงิน 2 ชนิด";
	// }
	// }
}
