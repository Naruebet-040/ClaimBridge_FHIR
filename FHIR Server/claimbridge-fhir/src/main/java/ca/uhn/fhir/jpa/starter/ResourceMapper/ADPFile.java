package ca.uhn.fhir.jpa.starter.ResourceMapper;

import static com.mongodb.client.model.Filters.*;

//import static org.mockito.ArgumentMatchers.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
// import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
// import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.Document;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Claim.DetailComponent;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.hl7.fhir.r4.model.Claim.SubDetailComponent;
import org.hl7.fhir.r4.model.Claim.SupportingInformationComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Money;
import org.hl7.fhir.r4.model.Quantity;
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
public class ADPFile {
	private static final String FHIR_SERVER_URL = "http://localhost:8080/fhir"; // URL ของ HAPI FHIR Server
	private static final SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);

	@PostMapping("/upload-adp")
	public String uploadFile(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			return "File is empty!";
		}

		List<Claim> adps = parseTextFile(file);
		return saveToFhirServer(adps); // ส่งข้อมูลไปยัง FHIR Server
	}

	private List<Claim> parseTextFile(MultipartFile file) {
		List<Claim> adps = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
			String line;
			int sequence = 1; // เริ่มจาก sequence 1
			// อ่าน Header
			String[] headers = br.readLine().split("\\|", -1);

			while ((line = br.readLine()) != null) {
				String[] data = line.split("\\|", -1);
				if (data.length < headers.length) {
					continue;
				}

				// Map เก็บชื่อคอลัมน์ -> ค่าข้อมูล
				Map<String, String> rowData = new HashMap<>();
				for (int i = 0; i < headers.length; i++) {
					rowData.put(headers[i], data[i]);
				}

				// ดึงค่า HCODE จาก Patient ID
				// String hcode = getHcodeFromPatient(fields[6]);

				// สร้าง Claim FHIR Resource
				Claim adp = new Claim();

				CodeableConcept type = new CodeableConcept();
				type.setText("ADP"); // ตั้งข้อความตรง ๆ โดยไม่ต้องใช้ Coding
				adp.setType(type);

				// ----- เพิ่ม Extension ----- //
				Extension projectcode = new Extension();
				projectcode.setUrl("https://fhir-ig.sil-th.org/th/extensions/StructureDefinition/ex-chi-project-code");
				projectcode.setValue(new StringType("HOSPIC"));

				adp.addExtension(projectcode);

				adp.setStatus(Claim.ClaimStatus.ACTIVE); // ตั้งค่า Status
				adp.setCreated(inputFormat.parse(rowData.get("DATEOPD"))); // DATEOPD
				if (rowData.get("HN") != null) {
					adp.setPatient(new Reference("Patient?identifier=" + rowData.get("HN"))); // HN ในกรณีเป็นผู้ป่วยนอก
				}
				// adp.setPatient(new Reference("Patient?identifier=" + fields[1])); //AN
				// ในกรณีเป็นผู้ป่วยใน
				adp.setProvider(new Reference("Practitioner?identifier=" + rowData.get("PROVIDER")));// PROVIDER

				// // ---- เพิ่ม HN Identifier ---- //
				// adp.addIdentifier()
				// .setSystem("https://terms.sil-th.org/hcode/5/" + hcode + "/HN")
				// .setValue(fields[0]) // HN
				// .setType(new CodeableConcept()
				// .addCoding(new Coding()
				// .setSystem("https://terms.sil-th.org/core/CodeSystem/cs-th-identifier-type")
				// .setCode("localHn")));

				// ---- เพิ่ม Claim Item (ค่าใช้จ่ายแบบละเอียด) ---- //
				ItemComponent adpitem = new Claim.ItemComponent();
				adpitem.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				adpitem.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setSystem("https://terms.sil-th.org/core/ValueSet/vs-eclaim-adp-type")
								.setCode(rowData.get("TYPE")) // TYPE
								.setDisplay(getChargeADPItemDisplay(rowData.get("TYPE"))))
						.addCoding(new Coding()
								.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-eclaim-medication-category")
								.setCode(rowData.get("USE_STATUS")) // USE_STATUS
								.setDisplay(getUseStatusDisplay(rowData.get("USE_STATUS")))));
				// สร้าง DetailComponent
				DetailComponent detail = new DetailComponent();

				// สร้าง SubDetailComponent และเพิ่ม Serial Number
				SubDetailComponent subDetail = new SubDetailComponent();
				subDetail.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("SERIALNO") // fields[11]
								.setDisplay(rowData.get("SERIALNO"))));

				// เพิ่ม SubDetail เข้าไปใน Detail
				detail.addSubDetail(subDetail);

				// เพิ่ม Detail เข้าไปใน Item
				adpitem.addDetail(detail);

				// set quantity
				adpitem.setQuantity(new Quantity().setCode(rowData.get("QTY"))); // QTY
				// Set unitPrice
				adpitem.setUnitPrice(
						new Money().setValue(Double.parseDouble(rowData.get("RATE"))).setCurrency("THB")); // RATE

				adp.addItem(adpitem);

				// QTYDAY (จำนวนวันที่ขอเบิก)
				ItemComponent qtydayItem = new ItemComponent();
				qtydayItem.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				qtydayItem.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("3") // ค่า Type = 3 (ค่าบริการอื่นๆ) fields[15]
								.setDisplay("ค่าบริการอื่นๆ ที่ยังไม่ได้จัดหมวด")));
				qtydayItem.setQuantity(new Quantity().setValue(5)); // จำนวนวันที่ขอเบิก 5 วัน rowData.get("QTYDAY")

				// TMLTCODE (รหัสการตรวจ TMLT)
				ItemComponent tmltItem = new ItemComponent();
				tmltItem.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				tmltItem.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("350501")// fields[16] rowData.get("TMLTCODE")
								.setDisplay("รหัสการตรวจ TMLT ตาม สมสท.")));

				// BI (Barthel ADL Index)
				ItemComponent biItem = new ItemComponent();
				biItem.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				biItem.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("999") // fields[18] rowData.get("BI")
								.setDisplay("ค่า Barthel ADL Index")));
				biItem.setQuantity(new Quantity().setValue(90)); // กำหนดค่า BI = 90

				// ITMESRC (ประเภทของรหัส)
				ItemComponent itemsrcItem = new ItemComponent();
				itemsrcItem.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				itemsrcItem.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("2") // fields[20] rowData.get("ITMESRC")
								.setDisplay("รหัสกรมบัญชีกลาง")));

				// DCIP/E_screen (รหัสคัดกรอง)
				ItemComponent dcipItem = new ItemComponent();
				dcipItem.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				dcipItem.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("28") // fields[24] rowData.get("DCIP")
								.setDisplay("คัดกรอง Positive"))); // ตัวอย่างให้ค่าเป็น 28 = positive

				// เพิ่มข้อมูล Item ต่างๆ เข้า adp
				adp.addItem(qtydayItem);
				adp.addItem(tmltItem);
				adp.addItem(biItem);
				adp.addItem(itemsrcItem);
				adp.addItem(dcipItem);

				// ---- เพิ่ม Claim Diagnosis ---- //
				Claim.DiagnosisComponent cancerdiagnosis = new Claim.DiagnosisComponent();
				cancerdiagnosis.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				cancerdiagnosis.setDiagnosis(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("ประเภทการรักษามะเร็ง")// CA_TYPE
								.setDisplay(rowData.get("CA_TYPE") + " = Visit"))
						.addCoding(new Coding()
								.setSystem("https://terms.sil-th.org/core/CodeSystem/cs-eclaim-cancer-type")
								.setCode(rowData.get("CAGCODE"))// CAGCODE
								.setDisplay(getCancerTypeDisplay(rowData.get("CAGCODE")))));
				Claim.DiagnosisComponent coviddiagnosis = new Claim.DiagnosisComponent();
				coviddiagnosis.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				coviddiagnosis.setDiagnosis(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("LAB COVID")// STATUS1 LAB COVID
								.setDisplay(getLabCovidDisplay(rowData.get("STATUS1")))));

				adp.addDiagnosis(cancerdiagnosis);
				adp.addDiagnosis(coviddiagnosis);

				adp.getTotal().setValue(Double.parseDouble(rowData.get("TOTAL"))).setCurrency("THB");// TOTAL

				// ---- เพิ่ม Claim SupportingInformation ---- //
				SupportingInformationComponent clinicSupport = new SupportingInformationComponent();
				clinicSupport.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				// Clinic
				clinicSupport.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setSystem("https://terms.sil-th.org/core/ValueSet/vs-eclaim-clinic")
								.setCode(rowData.get("CLINIC"))// CLINIC
								.setDisplay(getClinicDisplay(rowData.get("CLINIC")))));

				SupportingInformationComponent gravidaSupport = new SupportingInformationComponent();
				gravidaSupport.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				gravidaSupport.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("GRAVIDA")
								.setDisplay("ครรภ์ที่ :" + rowData.get("GRAVIDA"))) // fields[22]
				);
				SupportingInformationComponent gravidaWeekSupport = new SupportingInformationComponent();
				gravidaWeekSupport.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				gravidaWeekSupport.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("GA_WEEK")
								.setDisplay("อายุครรภ์ปัจจุบัน (สัปดาห์) : " + rowData.get("GA_WEEK"))) // fields[23]
				);
				SupportingInformationComponent lmpSupport = new SupportingInformationComponent();
				lmpSupport.setSequence(sequence); // ตั้งค่า sequence
				sequence++; // เพิ่มลำดับขึ้น 1
				lmpSupport.setCategory(new CodeableConcept()
						.addCoding(new Coding()
								.setCode("LMP")
								.setDisplay("วันแรกของการมีประจำเดือนครั้งสุดท้าย : " + rowData.get("LMP"))) // fields[25]
				);

				adp.addSupportingInfo(clinicSupport);
				adp.addSupportingInfo(gravidaSupport);
				adp.addSupportingInfo(gravidaWeekSupport);
				adp.addSupportingInfo(lmpSupport);

				// add adp ลงไปใน adps
				adps.add(adp);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return adps;
	}

	private long countClaimsByType(String claimTypeText) {
		try (MongoClient mongoClient = MongoClients.create(
				"mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/")) {
			MongoDatabase database = mongoClient.getDatabase("Bangkok_DB");
			MongoCollection<Document> collection = database.getCollection("Claim File");

			// ใช้ Filters.eq เพื่อตรวจสอบ field ที่อยู่ใน nested document
			long count = collection.countDocuments(eq("type.text", "APD"));
			return count;
		}
	}

	private String saveToFhirServer(List<Claim> adps) {
		FhirContext ctx = FhirContext.forR4();
		IGenericClient client = ctx.newRestfulGenericClient(FHIR_SERVER_URL);

		List<String> responseList = new ArrayList<>();
		// ดึงจำนวน ADP ที่มีอยู่ใน MongoDB
		long existingADPCount = countClaimsByType("ADP");
		int skipped = 0;

		System.out.println("existingADPCount: " + existingADPCount);
		System.out.println("skipped: " + skipped);

		for (Claim adp : adps) {
			try {
				// ถ้ามี ADP อยู่แล้วใน MongoDB เท่ากับหรือมากกว่าจำนวนใหม่ -> ข้าม
				if (skipped < existingADPCount) {
					skipped++;
					continue;
				}

				MethodOutcome outcome = client.create().resource(adp).execute();
				// ตรวจสอบว่า FHIR Server ส่งกลับอะไร
				if (outcome.getCreated()) {
					responseList.add("Uploaded adp Claim: " + outcome.getId().getIdPart());
				} else {
					responseList.add("Failed to upload adp Claim: " + adp.getId());
				}

				// แปลง Patient เป็น JSON
				String json = ctx.newJsonParser().encodeResourceToString(outcome.getResource());

				// บันทึกข้อมูลลง MongoDB หลังจากส่งข้อมูลไป FHIR server
				saveToMongo(json);

			} catch (Exception e) {
				responseList.add("Error uploading Claim: " + adp.getId() + " - " + e.getMessage());
				e.printStackTrace(); // พิมพ์ stack trace เพื่อ debug
			}
		}

		if (skipped >= existingADPCount && existingADPCount > 0) {
			System.out.println("❌ (ADP) Skipping upload " + (skipped) + " record, already in system.");
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

	private String getChargeADPItemDisplay(String code) {
		switch (code) {
			case "1":
				return "HC (OPD)";
			case "2":
				return "Instrument (หมวด 2)";
			case "3":
				return "ค่าบริการอื่นๆ ที่ยังไม่ได้จัดหมวด";
			case "4":
				return "ค่าส่งเสริมป้องกัน/บริการเฉพาะ";
			case "5":
				return "Project code";
			case "6":
				return "การรักษามะเร็งตามโปรโตคอล";
			case "7":
				return "การรักษาโรคมะเร็งด้วยรังสีวิทยา";
			case "8":
				return "OP REFER และ รายการ Fee Schedule (สามารถใช้ชื่อ TYPE หรือ TYPEADP ได้)";
			case "9":
				return "ตรวจวินิจฉัยด้วยวิธีพิเศษอื่นๆ (หมวด 9)";
			case "10":
				return "ค่าห้อง/ค่าอาหาร (หมวด 1)";
			case "11":
				return "เวชภัณฑ์ที่ไม่ใช่ยา (หมวด 5)";
			case "12":
				return "ค่าบริการทันตกรรม (หมวด 13)";
			case "13":
				return "ค่าบริการฝังเข็ม (หมวด 15)";
			case "14":
				return "บริการโลหิตและส่วนประกอบของโลหิต (หมวด 6)";
			case "15":
				return "ตรวจวินิจฉัยทางเทคนิคการแพทย์และพยาธิวิทยา (หมวด 7)";
			case "16":
				return "ค่าตรวจวินิจฉัยและรักษาทางรังสีวิทยา (หมวด 8)";
			case "17":
				return "ค่าบริการทางการพยาบาล (หมวด 12)";
			case "18":
				return "อุปกรณ์และเครื่องมือทางการแพทย์ (หมวด 10)";
			case "19":
				return "ทำหัตถการและวิสัญญี (หมวด 11)";
			case "20":
				return "ค่าบริการทางกายภาพบำบัดและเวชกรรมฟื้นฟู (หมวด 14)";
			default:
				return "ไม่พบรายการ"; // กรณีไม่มีข้อมูล
		}
	}

	private String getUseStatusDisplay(String use) {
		switch (use) {
			case "1":
				return "ใช้ในโรงพยาบาล";
			case "2":
				return "ใช้ที่บ้าน";
			case "3":
				return "ยาเกิน 2 สัปดาห์ (กลับบ้าน)";
			case "4":
				return "ยาโรคเรื้อรัง (กลับบ้าน)";
			default:
				return "ไม่พบข้อมูล";
		}
	}

	private String getLabCovidDisplay(String labcovid) {
		switch (labcovid) {
			case "1":
				return "Positive";
			case "0":
				return "Negative";
			default:
				return "ไม่พบข้อมูล";
		}
	}

	private String getCancerTypeDisplay(String cancer) {
		switch (cancer) {
			case "Bd":
				return "Bladder";
			case "Br":
				return "Breast";
			case "Ch":
				return "Cholangiocarcinoma";
			case "Cr":
				return "Colon & Rectum";
			case "Cx":
				return "Cervix";
			case "Es":
				return "Esophagus";
			case "Ln":
				return "Lung (Non small cell)";
			case "Lu":
				return "Lung (Small cell)";
			case "Na":
				return "Nasopharynx";
			case "Ov":
				return "Ovary";
			case "Ps":
				return "Prostate";
			case "Gca":
				return "มะเร็งทั่วไป";
			default:
				return "ไม่พบข้อมูล"; // กรณีไม่มีข้อมูล
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
