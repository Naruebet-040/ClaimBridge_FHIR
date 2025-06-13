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


### ⚙️ Configuration

* Move the `FHIR_Dataset` folder to the path `C:/FHIR_Dataset`
* ✅ Make sure MongoDB is running before starting the server.

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

#### Test via web browser

Open your browser and navigate to:
```bash
http://localhost:8080/fhir/Patient
```

You should see a FHIR Bundle response containing any existing Patient resources.



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

