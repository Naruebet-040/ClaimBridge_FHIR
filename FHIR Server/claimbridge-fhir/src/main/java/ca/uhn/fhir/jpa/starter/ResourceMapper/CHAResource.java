package ca.uhn.fhir.jpa.starter.ResourceMapper;

import static com.mongodb.client.model.Filters.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
//import java.text.ParseException;
//import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
//import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
//import org.hl7.fhir.Date;
//import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Money;
//import org.hl7.fhir.r4.model.Patient;
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
public class CHAResource {

	private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir"; // URL ของ HAPI FHIR Server
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

	@PostMapping("/upload-char")
	public String uploadFile(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			return "File is empty!";
		}

		List<Claim> chas = parseTextFile(file);
		return saveToFhirServer(chas); // ส่งข้อมูลไปยัง FHIR Server
	}

	private List<Claim> parseTextFile(MultipartFile file) {
		List<Claim> chas = new ArrayList<>();
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
				} // CHA

				// สร้าง Claim FHIR Resource
				Claim claim = new Claim();

				CodeableConcept type = new CodeableConcept();
				type.setText("CHAR"); // ตั้งข้อความตรง ๆ โดยไม่ต้องใช้ Coding
				claim.setType(type);

				claim.setStatus(Claim.ClaimStatus.ACTIVE);
				claim.setUse(Claim.Use.CLAIM);
				claim.setPatient(new Reference("Patient?identifier=" + rowData.get("HN"))); // CHA: HN
				claim.setCreated(dateFormat.parse(rowData.get("DATE"))); // CHA: DATE

				// เพิ่ม HN Identifier
				claim.addIdentifier()
						.setSystem("https://terms.sil-th.org/hcode/5/" + "99999" + "/HN")
						.setValue(rowData.get("HN")) // CHA: HN
						.setType(new CodeableConcept()
								.addCoding(new Coding()
										.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
										.setCode("localHn")));

				// เพิ่ม AN Identifier (ถ้ามีค่า)
				if (!rowData.get("AN").isEmpty()) {
					claim.addIdentifier()
							.setSystem("https://terms.sil-th.org/hcode/5/" + "99999" + "/AN")
							.setValue(rowData.get("AN")) // CHA: AN
							.setType(new CodeableConcept()
									.addCoding(new Coding()
											.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
											.setCode("localAn")));
				}

				// เพิ่ม Claim Item
				Claim.ItemComponent item = new Claim.ItemComponent();
				item.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				item.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setSystem("https://terms.sil-th.org/core/ValueSet/vs-eclaim-charge-item")
								.setCode(rowData.get("CHRGITEM"))
								.setDisplay(getChargeItemDisplay(rowData.get("CHRGITEM"))))); // CHRGITEM
				item.setUnitPrice(new Money().setValue(Double.parseDouble(rowData.get("AMOUNT"))).setCurrency("THB")); // AMOUNT

				claim.addItem(item);

				chas.add(claim);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return chas;
	}

	private long countClaimsByType(String claimTypeText) {
		try (MongoClient mongoClient = MongoClients.create(
				"mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
			MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
			MongoCollection<Document> collection = database.getCollection("Claim File");

			// ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
			long count = collection.countDocuments(eq("type.text", "CHAR"));
			return count;
		}
	}

	private String saveToFhirServer(List<Claim> chas) {
		FhirContext ctx = FhirContext.forR4();
		IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

		List<String> responseList = new ArrayList<>();
		// ดึงจำนวน ADP ที่มีอยู่ใน MongoDB
		long existingCHARCount = countClaimsByType("CHAR");
		int skipped = 0;

		for (Claim cha : chas) {
			try {
				// ถ้ามี CHAR อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
				if (skipped < existingCHARCount) {
					skipped++;
					continue;
				}

				MethodOutcome outcome = client.create().resource(cha).execute();
				// ตรวจสอบว่า FHIR Server ส่งกลับอะไร
				if (outcome.getCreated()) {
					responseList.add("Uploaded Claim: " + outcome.getId().getIdPart());
				} else {
					responseList.add("Failed to upload Claim: " + cha.getId());
				}

				// แปลง Patient เป็น JSON
				String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

				// บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
				saveToMongo(json);

			} catch (Exception e) {
				responseList.add("Error uploading Claim: " + cha.getId() + " - " + e.getMessage());
				e.printStackTrace(); // พิมพ์ stack trace เพื่อ debug
			}
		}
		if (skipped >= existingCHARCount && existingCHARCount > 0) {
			System.out.println("❌ (CHAR) Skipping upload " + (skipped) + " record, already in system.");
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

	private String getChargeItemDisplay(String code) {
		switch (code) {
			case "1":
				return "เบิกได้";
			case "11":
				return "ค่าห้อง/ค่าอาหาร";
			case "21":
				return "อวัยวะเทียม/อุปกรณ์ในการบำบัดรักษา";
			case "31":
				return "ยาและสารอาหารทางเส้นเลือดที่ใช้ใน รพ.";
			case "41":
				return "ยาที่นำไปใช้ต่อที่บ้าน";
			case "51":
				return "เวชภัณฑ์ที่ไม่ใช่ยา";
			case "61":
				return "บริการโลหิตและส่วนประกอบของโลหิต";
			case "71":
				return "ตรวจวินิจฉัยทางเทคนิคการแพทย์และพยาธิวิทยา";
			case "81":
				return "ตรวจวินิจฉัยและรักษาทางรังสีวิทยา";
			case "91":
				return "ตรวจวินิจฉัยโดยวิธีพิเศษอื่น ๆ";
			case "A1":
				return "อุปกรณ์ของใช้และเครื่องมือทางการแพทย์";
			case "B1":
				return "ทำหัตถการ และบริการวิสัญญี";
			case "C1":
				return "ค่าบริการทางการพยาบาล";
			case "D1":
				return "บริการทางทันตกรรม";
			case "E1":
				return "บริการทางกายภาพบำบัด และเวชกรรมฟื้นฟู";
			case "F1":
				return "บริการฝังเข็ม/การบำบัดของผู้ประกอบโรคศิลปะอื่น ๆ";
			case "G1":
				return "ค่าห้องผ่าตัดและห้องคลอด";
			case "H1":
				return "ค่าธรรมเนียมบุคลากรทางการแพทย์";
			case "I1":
				return "บริการอื่น ๆ และส่งเสริมป้องกันโรค";
			case "J1":
				return "บริการอื่น ๆ ที่ยังไม่จัดหมวด";
			case "2":
				return "เบิกได้";
			case "12":
				return "ค่าห้อง/ค่าอาหาร";
			case "22":
				return "อวัยวะเทียม/อุปกรณ์ในการบำบัดรักษา";
			case "32":
				return "ยาและสารอาหารทางเส้นเลือดที่ใช้ใน รพ.";
			case "42":
				return "ยาที่นำไปใช้ต่อที่บ้าน";
			case "52":
				return "เวชภัณฑ์ที่ไม่ใช่ยา";
			case "62":
				return "บริการโลหิตและส่วนประกอบของโลหิต";
			case "72":
				return "ตรวจวินิจฉัยทางเทคนิคการแพทย์และพยาธิวิทยา";
			case "82":
				return "ตรวจวินิจฉัยและรักษาทางรังสีวิทยา";
			case "92":
				return "ตรวจวินิจฉัยโดยวิธีพิเศษอื่น ๆ";
			case "A2":
				return "อุปกรณ์ของใช้และเครื่องมือทางการแพทย์";
			case "B2":
				return "ทำหัตถการ และบริการวิสัญญี";
			case "C2":
				return "ค่าบริการทางการพยาบาล";
			case "D2":
				return "บริการทางทันตกรรม";
			case "E2":
				return "บริการทางกายภาพบำบัด และเวชกรรมฟื้นฟู";
			case "F2":
				return "บริการฝังเข็ม/การบำบัดของผู้ประกอบโรคศิลปะอื่น ๆ";
			case "G2":
				return "ค่าห้องผ่าตัดและห้องคลอด";
			case "H2":
				return "ค่าธรรมเนียมบุคลากรทางการแพทย์";
			case "I2":
				return "บริการอื่น ๆ และส่งเสริมป้องกันโรค";
			case "J2":
				return "บริการอื่น ๆ ที่ยังไม่จัดหมวด";
			case "K1":
				return "พรบ.";
			default:
				return "ไม่ทราบรายการ"; // กรณีไม่มีข้อมูล
		}
	}
}
