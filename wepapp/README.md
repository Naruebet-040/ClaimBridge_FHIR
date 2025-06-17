<p align="right"><a href="../README.md">กลับหน้าแรก</a></p>

# 🌐 wepapp

**wepapp** เป็นระบบเว็บแอปที่พัฒนาโดยใช้ HTML, CSS, JavaScript ร่วมกับ Node.js และ Express โดยมีจุดประสงค์เพื่อให้บริการหน้าเว็บแบบ static และ dynamic เช่น `index.html`, `login.html`, และ `resourcelist.html`

---

## ✅ ข้อมูลเบื้องต้น

- **Frontend**: HTML, CSS, JavaScript
- **Backend**: Node.js + Express
- **Static UI**: ไม่มีการใช้ Framework (เช่น React หรือ Vue)
- เหมาะสำหรับระบบแสดงผลเบื้องต้นผ่าน UI แบบ Static

---

## ⚙️ การติดตั้งและเริ่มใช้งาน

### 1. ติดตั้ง Node.js
แนะนำให้ใช้เวอร์ชัน 18 ขึ้นไป

### 2. ติดตั้ง Dependencies
```bash
npm install
```

### 3. รันโปรเจกต์
```bash
npm start
```

ระบบจะพร้อมใช้งานที่:
```bash
http://localhost:3000
```
---

## 🗂️ โครงสร้างโปรเจกต์
```bash

wepapp/
├── css/               # ไฟล์ CSS สำหรับตกแต่งหน้าเว็บ
├── fonts/             # ฟอนต์ที่ใช้ในระบบ (ถ้ามี)
├── images/            # ไฟล์รูปภาพประกอบ
├── js/                # JavaScript ฝั่ง client
├── node_modules/      # ไลบรารีที่ติดตั้งจาก npm
├── index.html         # หน้าเว็บหลัก
├── login.html         # หน้าเข้าสู่ระบบ
├── resourcelist.html  # หน้ารายการทรัพยากร
├── server.js          # Express Server
├── package.json       # รายละเอียด dependencies และสคริปต์
└── README.md          # ไฟล์อธิบายโปรเจกต์

```

---

## 🧪 การทดสอบเบื้องต้น

### 1. เปิดเบราว์เซอร์ที่ http://localhost:3000

### 2. ตรวจสอบว่าโหลดหน้า index.html ได้ตามปกติ

### 3. ลองเข้าหน้า http://localhost:3000/login.html และ resourcelist.html

### 4. ตรวจสอบว่า CSS และ JS โหลดทำงานได้ครบ

---

## 📌 หมายเหตุ


---
---

# 🌐 wepapp (draft version)

ระบบ webapp เป็น frontend สำหรับให้ผู้ใช้เข้าถึงข้อมูลผ่าน UI โดยพัฒนาด้วย React + Vite

---

## ✅ ข้อมูลเบื้องต้น

* React 18
* Vite สำหรับ build และ dev server
* ใช้ Tailwind CSS หรือ Emotion (ขึ้นกับโปรเจกต์จริง)

---

## ⚙️ การติดตั้งและใช้งาน

### 1. ตรวจสอบ Node.js

* แนะนำ Node.js เวอร์ชัน 18 ขึ้นไป

### 2. ติดตั้ง dependencies และรันโปรเจกต์

```bash
cd wepapp
npm install
npm run dev
```

ระบบจะพร้อมใช้งานที่:

```
http://localhost:3000
```

---

## 🗂️ โครงสร้างโปรเจกต์หลัก

* `src/` : โค้ดหลักของระบบ

  * `pages/` : หน้าแต่ละหน้า
  * `components/` : ส่วนประกอบ UI
  * `lib/` : คลังฟังก์ชัน, context, API service ฯลฯ
* `public/` : ไฟล์ static เช่น favicon, logo

---

## 🧪 การทดสอบ

* ตรวจสอบว่าโหลดหน้าเว็บได้สำเร็จที่ [http://localhost:5173](http://localhost:5173)
* ตรวจสอบการดึงข้อมูลจาก FHIR Server

---

## 📌 หมายเหตุ

* หากต้องการ build สำหรับ production:

```bash
npm run build
```

* การเชื่อมต่อ backend สามารถตั้งค่าที่ไฟล์ `.env`
