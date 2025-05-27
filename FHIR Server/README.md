<p align="right"><a href="../README.md">กลับหน้าแรก</a></p>


## 📘 FHIR Server with HAPI FHIR JPA (MongoDB) (~/FHIR Server)

### 🔰 Overview

This project is a FHIR Server based on **HAPI FHIR (R4)** implemented using **Spring Boot** and configured to use **MongoDB** as the database. The server supports standard FHIR RESTful operations on resources such as `Patient`, `Observation`, and `Claim`.

---

### 🧾 Prerequisites

Before running this project, ensure you have the following installed:

* **Java 17+**
* **Apache Maven 3.6+**
* **MongoDB 6.0+** (running locally or in a container)

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

> Maintained by ClaimBridge FHIR @ Organization Name

