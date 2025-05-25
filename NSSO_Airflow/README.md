<p align="right"><a href="../README.md">กลับหน้าแรก</a></p>


# 📊 NSSO\_Airflow (draft version)

NSSO\_Airflow เป็นระบบ workflow สำหรับการดึง แปลง และโหลดข้อมูล (ETL) โดยใช้ Apache Airflow และ Docker

---

## ✅ ข้อมูลเบื้องต้น

* ใช้ Apache Airflow สำหรับ orchestration
* ใช้ Docker Compose สำหรับรันระบบ

---

## ⚙️ การติดตั้งและใช้งาน

### 1. ตรวจสอบ Docker และ Docker Compose

* Docker เวอร์ชันล่าสุด
* Docker Compose

### 2. รันระบบ Airflow

```bash
cd NSSO_Airflow
docker-compose up
```

### 3. เข้าสู่ระบบผ่าน Web UI

```
http://localhost:8080
```

Username/Password เริ่มต้น:

* user: `airflow`
* password: `airflow`

---

## 🗂️ โครงสร้างโปรเจกต์หลัก

* `dags/` : DAG workflow ต่าง ๆ
* `docker-compose.yml` : คอนฟิกหลักสำหรับรัน Airflow
* `requirements.txt` : กำหนด Python packages เพิ่มเติมที่ต้องการติดตั้งใน container

---

## 🧪 การทดสอบ

* ตรวจสอบว่ามี DAG ปรากฏใน Airflow UI
* สามารถ trigger DAG ได้จากหน้าเว็บ

---

## 📌 หมายเหตุ

* DAG ที่สร้างควรวางในโฟลเดอร์ `dags/`
* หาก container ขึ้นไม่สมบูรณ์ อาจต้องเพิ่ม RAM/CPU สำหรับ Docker Desktop
