package ca.uhn.fhir.jpa.starter.ResourceMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
//import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Claim.InsuranceComponent;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
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
public class INSFlie {
	private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir"; // URL ของ HAPI FHIR Server
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

	@PostMapping("/upload-ins")
	public String uploadFile(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			return "File is empty!";
		}

		List<Claim> INSFiles = parseTextFile(file);
		return saveToFhirServer(INSFiles); // ส่งข้อมูลไปยัง FHIR Server
	}

	private List<Claim> parseTextFile(MultipartFile file) {
		List<Claim> INSFiles = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
			String line;
			int sequence = 1; // เริ่มจาก sequence 1
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
				// String hcode = getHcodeFromPatient(fields[4]);

				// **สร้าง Claim FHIR Resource**
				Claim claim = new Claim();
				claim.setStatus(Claim.ClaimStatus.ACTIVE);
				claim.setUse(Claim.Use.CLAIM);
				claim.setPatient(new Reference("Patient?identifier=" + rowData.get("HN"))); // HN
				claim.setCreated(dateFormat.parse(rowData.get("DATEEXP"))); // DATEEXP
				claim.addInsurance(
						new InsuranceComponent().setCoverage(new Reference("Coverage?type=" + rowData.get("INSCL"))
								.setDisplay(getCoverageTypeDisplay(rowData.get("INSCL"))))); // INSCL

				// // เพิ่ม HN Identifier
				// claim.addIdentifier()
				// .setSystem("https://terms.sil-th.org/hcode/5/" + fields[4] + "/HN")
				// //fields[4] = HCODE
				// .setValue(fields[0]) // HN
				// .setType(new CodeableConcept()
				// .addCoding(new Coding()
				// .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
				// .setCode("localHn")));

				// // เพิ่ม AN Identifier (ถ้ามีค่า)
				// if (!fields[14].isEmpty()) {
				// claim.addIdentifier()
				// .setSystem("https://terms.sil-th.org/hcode/5/" + fields[4] + "/AN")
				// .setValue(fields[14]) // AN
				// .setType(new CodeableConcept()
				// .addCoding(new Coding()
				// .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
				// .setCode("localAn")));
				// }

				// **หมายเลขประจำตัวประชาชน HN**
				claim.addIdentifier()
						.setType(new CodeableConcept()
								.addCoding(new Coding()
										.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
										.setCode("cid")
										.setDisplay("เลขประจำตัวประชาชนไทย")))
						.setSystem("https://terms.sil-th.org/id/th-cid")
						.setValue(rowData.get("CID"));// CID

				ItemComponent htypeitem = new Claim.ItemComponent();
				htypeitem.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				htypeitem.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setSystem("https://terms.sil-th.org/core/ValueSet/vs-eclaim-procedure-type")
								.setCode(rowData.get("HTYPE")) // HTYPE fields[18]
								.setDisplay(getHTypeDisplay(rowData.get("HTYPE")))));

				claim.addItem(htypeitem);

				INSFiles.add(claim);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return INSFiles;
	}

	// ฟังก์ชันใหม่ในการตรวจสอบข้อมูลซ้ำ
	private boolean isDuplicatePatientRef(String Ref) {
		MongoClient mongoClient = MongoClients
				.create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/");
		MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
		MongoCollection<Document> collection = database.getCollection("Claim File");

		// ค้นหาข้อมูล Patient ใน MongoDB โดยใช้รหัสประจำตัว (HN)
		Document existingDocument = collection.find(new Document("patient.reference", Ref)).first();

		mongoClient.close();

		return existingDocument != null;
	}

	private String findPatientIdByHN(String hn) {
		try (MongoClient mongoClient = MongoClients
				.create("mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
			MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
			MongoCollection<Document> collection = database.getCollection("Patient File");

			// ค้นหาข้อมูล Patient โดยใช้ HN
			Document patientDoc = collection.find(new Document("identifier.value", hn)).first();

			// หากพบเอกสารจะดึงค่า "id" จากฟิลด์ "id"
			if (patientDoc != null) {
				return patientDoc.getString("id"); // คืนค่าจากฟิลด์ "id"
			} else {
				return null; // ถ้าไม่พบข้อมูล
			}
		} catch (Exception e) {
			System.err.println("Error finding patient ID by HN: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	private String saveToFhirServer(List<Claim> INSFiles) {
		FhirContext ctx = FhirContext.forR4();
		IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

		List<String> responseList = new ArrayList<>();
		for (Claim claim : INSFiles) {
			try {

				// ตรวจสอบเฉพาะ Encounter ว่าซ้ำหรือไม่
				if (claim instanceof Claim) {
					String patientReference = claim.getPatient().getReference();

					// ตรวจสอบว่า patientReference มี '?' และแยก identifier ออก
					if (patientReference != null && patientReference.contains("?identifier=")) {
						String hn = patientReference.split("\\?identifier=")[1];

						// Log
						// System.out.println("Patient ID to check: " + hn);
						// System.out.println("Patient/" + findPatientIdByHN(hn));

						// Setid
						String patientId = "Patient/" + findPatientIdByHN(hn);
						// ตรวจสอบว่า patientId ซ้ำหรือไม่ใน MongoDB
						if (isDuplicatePatientRef(patientId)) {
							System.out
									.println("❌ (INS) HN: " + hn + " is already duplicated in the system!");
							continue; // ข้ามการอัปโหลด
						}
					} else {
						System.out.println("❌ Invalid patient reference format: " + patientReference);
						continue; // ข้ามการอัปโหลดหากข้อมูลไม่ถูกต้อง
					}
				}

				MethodOutcome outcome = client.create().resource(claim).execute();
				// ตรวจสอบว่า FHIR Server ส่งกลับอะไร
				if (outcome.getCreated()) {
					responseList.add("Uploaded INS Claim: " + outcome.getId().getIdPart());
				} else {
					responseList.add("Failed to upload INS Claim: " + claim.getId());
				}

				// แปลง Patient เป็น JSON
				String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

				// บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
				saveToMongo(json);

			} catch (Exception e) {
				responseList.add("Error uploading INS Claim: " + claim.getId() + " - " + e.getMessage());
				e.printStackTrace(); // พิมพ์ stack trace เพื่อ debug
			}
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

	private String getCoverageTypeDisplay(String coverage) {
		switch (coverage) {
			case "UCS":
				return "สิทธิ UC";
			case "OFC":
				return "ข้าราชการ";
			case "SSS":
				return "ประกันสังคม";
			case "LGO":
				return "อปท";
			case "NHS":
				return "สิทธิเจ้าหน้าที่ สปสช.";
			default:
				return "ไม่พบข้อมูล"; // กรณีไม่มีข้อมูล
		}
	}

	private String getHTypeDisplay(String htype) {
		switch (htype) {
			case "1":
				return "Main Contractor";
			case "2":
				return "Sub Contractor";
			case "3":
				return "Supra Contractor";
			case "4":
				return "Excellent";
			case "5":
				return "Super tertiary";
			default:
				return "ไม่พบข้อมูล"; // กรณีไม่มีข้อมูล
		}
	}
}
