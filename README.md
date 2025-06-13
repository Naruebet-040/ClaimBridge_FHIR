
# ClaimBridge FHIR

ClaimBridge FHIR เป็นโครงการที่พัฒนาขึ้นเพื่อรองรับการจัดการข้อมูลเคลมสุขภาพในรูปแบบ HL7 FHIR มาตรฐานสากล โดยประกอบด้วยระบบ 3 ส่วนหลัก:

* 🔹 FHIR Server (Spring Boot + MongoDB)
* 🔹 Airflow สำหรับ ETL
* 🔹 Web Application สำหรับการเข้าถึงข้อมูลแบบ UI

---

## ⚙️ ความต้องการระบบ

* Java 17+
* MongoDB 6+
* Maven 3.6+
* Docker + Docker Compose
* Node.js 18+

---

## 📦 Clone the Project

```bash
git clone https://github.com/Naruebet-040/ClaimBridge_FHIR.git
cd ClaimBridge_FHIR/FHIR Server
```

---

## 📁 โครงสร้างโปรเจกต์

```
ClaimBridge_FHIR/
├── FHIR Server/       ← [📘 อ่านคู่มือ](./FHIR Server/README.md)
├── NSSO_Airflow/      ← [📘 อ่านคู่มือ](./NSSO_Airflow/README.md)
├── wepapp/            ← [📘 อ่านคู่มือ](./wepapp/README.md)
└── readme.md
```

---

## ▶️ วิธีใช้งานเบื้องต้น

```bash
# รัน FHIR Server
cd "FHIR Server"
mvn spring-boot:run

# รัน Airflow
cd ../NSSO_Airflow
docker-compose up

# รัน Webapp
cd ../wepapp
npm install && npm run dev
```

---

## 🔗 ลิงก์คู่มือย่อ

| Module                 | คู่มือการใช้งาน                                        |
| ---------------------- | ------------------------------------------------------ |
| 🏥 **FHIR Server**     | [📘 FHIR Server/README.md](./FHIR%20Server/README.md)  |
| 📊 **NSSO\_Airflow**   | [📘 NSSO\_Airflow/README.md](./NSSO_Airflow/README.md) |
| 🌐 **Web Application** | [📘 wepapp/README.md](./wepapp/README.md)              |


---

## 📜 License

MIT License (หรือตามที่องค์กรกำหนด)





