
# ClaimBridge FHIR

ClaimBridge FHIR is a project developed to support the management of health claim data following the international HL7 FHIR standard. It consists of three main systems:

* 🔹 FHIR Server (Spring Boot + MongoDB)
* 🔹 Airflow for ETL processes
* 🔹 Web Application for UI data access

---

## ⚙️ System Requirements

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

## 📁 Project Structure

```
ClaimBridge_FHIR/
├── FHIR Server/       ← [📘 Read the manual](./FHIR%20Server/README.md)
├── NSSO_Airflow/      ← [📘 Read the manual](./NSSO_Airflow/README.md)
├── wepapp/            ← [📘 Read the manual](./wepapp/README.md)
└── readme.md
```

### 📘 Read the manuals
| Module                 | User Guide                             |
| ---------------------- | ------------------------------------------------------ |
| 🏥 **FHIR Server**     | [📘 FHIR Server Setup](./FHIR%20Server/README.md)  |
| 📊 **NSSO\_Airflow**   | [📘 NSSO Airflow Setup](./NSSO_Airflow/README.md) |
| 🌐 **Web Application** | [📘 wepapp Setup](./wepapp/README.md)              |
| **ClaimBridge_FHIR**    | This file                                          |

---

## ▶️ Basic Usage

```bash

# Run FHIR Server
cd "FHIR Server"
mvn spring-boot:run

# Run Airflow
cd ../NSSO_Airflow
docker-compose up

# Run Webapp
cd ../wepapp
npm install && npm run dev

```

---







