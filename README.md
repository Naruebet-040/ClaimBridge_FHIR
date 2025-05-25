# ClaimBridge_FHIR (_Root_)

# 🧩 ClaimBridge\_FHIR

ClaimBridge\_FHIR เป็นโครงการที่พัฒนาขึ้นเพื่อรองรับการจัดการข้อมูลเคลมสุขภาพในรูปแบบ HL7 FHIR มาตรฐานสากล โดยประกอบด้วยระบบ 3 ส่วนหลัก:

* 🔹 FHIR Server (Spring Boot + MongoDB)
* 🔹 Airflow สำหรับ ETL
* 🔹 Web Application สำหรับการเข้าถึงข้อมูลแบบ UI

---

## 📁 โครงสร้างโปรเจกต์

```
ClaimBridge_FHIR/
├── FHIR Server/       ← [📘 อ่านคู่มือ](./FHIR%20Server/README.md)
├── NSSO_Airflow/      ← [📘 อ่านคู่มือ](./NSSO_Airflow/README.md)
├── wepapp/            ← [📘 อ่านคู่มือ](./wepapp/README.md)
└── readme.md
```

---

## ▶️ วิธีใช้งานเบื้องต้น

```bash
# รัน FHIR Server
cd "FHIR Server"
./mvnw spring-boot:run

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

## ⚙️ ความต้องการระบบ

* Java 17+
* MongoDB 6+
* Maven 3.6+
* Docker + Docker Compose
* Node.js 18+

---

## 📜 License

MIT License (หรือตามที่องค์กรกำหนด)





-------------------------------------------------------------

## 📘 FHIR Server with HAPI FHIR JPA (MongoDB) (~/FHIR Server)

### 🔰 Overview

This project is a FHIR Server based on **HAPI FHIR (R4)** implemented using **Spring Boot** and configured to use **MongoDB** as the database. The server supports standard FHIR RESTful operations on resources such as `Patient`, `Observation`, and `Claim`.

---

### 🧾 Prerequisites

Before running this project, ensure you have the following installed:

* **Java 17+**
* **Apache Maven 3.6+**
* **MongoDB 6.0+** (running locally or in a container)
* **Git** (optional)

---

### 📦 Clone the Project

```bash
git clone https://github.com/Naruebet-040/ClaimBridge_FHIR.git
cd ClaimBridge_FHIR/FHIR Server
```

---

### ⚙️ Configuration

Edit the MongoDB connection in `src/main/resources/application.yaml`:

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/fhirdb

hapi:
  fhir:
    version: R4
    rest:
      server-name: FHIR Mongo Server
      server-version: 1.0.0
```

> ✅ Make sure MongoDB is running before starting the server.

---

### 🚀 Run the Server

Run the application using Maven:

```bash
mvn spring-boot:run
```

Or build and run the jar:

```bash
mvn clean package
java -jar target/fhir-server.jar
```

---

### 🌐 Access the Server

After starting, access the FHIR server at:

```
http://localhost:8080/fhir
```

#### Test via `curl`

```bash
curl -X GET http://localhost:8080/fhir/Patient
```

#### Test via Postman

* Method: `GET`
* URL: `http://localhost:8080/fhir/Patient`

---

### 🧪 Sample Payload (Create Patient)

```bash
curl -X POST http://localhost:8080/fhir/Patient \
     -H "Content-Type: application/fhir+json" \
     -d '{
           "resourceType": "Patient",
           "name": [{ "use": "official", "family": "Smith", "given": ["John"] }],
           "gender": "male",
           "birthDate": "1980-01-01"
         }'
```

---

### 📁 Project Structure

```
src/
 └── main/
      ├── java/ca/uhn/fhir/jpa/starter/
      │    ├── config/
      │    ├── controller/
      │    └── service/
      └── resources/
           ├── application.yaml
           └── ...
```

---

### 💬 Common Issues

* **MongoDB connection error:** Ensure MongoDB is running at `localhost:27017`.
* **Port conflict:** Default port `8080` may already be in use.
* **Missing dependencies:** Run `mvn clean install` to resolve.

---

### 📚 References

* [HAPI FHIR Documentation](https://hapifhir.io/)
* [FHIR Specification](https://www.hl7.org/fhir/)
* [Spring Boot Docs](https://spring.io/projects/spring-boot)
* [MongoDB Docs](https://www.mongodb.com/docs/)

---

> Maintained by Your Team @ Organization Name
