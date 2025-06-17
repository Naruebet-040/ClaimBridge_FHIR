<p align="right"><a href="../README.md">กลับหน้าแรก</a></p>

# 🌐 wepapp

**wepapp** เป็นระบบเว็บแอปที่พัฒนาโดยใช้ HTML, CSS, JavaScript ร่วมกับ Node.js และ Express โดยมีจุดประสงค์เพื่อให้บริการหน้าเว็บแบบ static และ dynamic เช่น `index.html`, `login.html`, และ `resourcelist.html`

---

## ✅ ข้อมูลเบื้องต้น

- **Frontend**: HTML, CSS, JavaScript
- **Backend**: Node.js + Express

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

- เปิดเบราว์เซอร์ที่ http://localhost:3000
- ตรวจสอบว่าโหลดหน้า index.html ได้ตามปกติ
- ลองเข้าหน้า Sign in 
  ```bash
  username: NSSO
  password: NSSOpassword
  ```
- ลองเข้าหน้า Resource List
- ตรวจสอบว่า CSS และ JS โหลดทำงานได้ครบ

---

## 📌 หมายเหตุ
- หากต้องการเชื่อมต่อ API เพิ่มเติม สามารถปรับแต่งได้ใน server.js
- หากระบบจะเติบโตในอนาคต อาจพิจารณาย้ายไปใช้ React, Vue หรือ Framework อื่นๆ เพื่อจัดการ UI ที่ซับซ้อนมากขึ้น

