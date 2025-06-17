<p align="right"><a href="../README.md">Back to Home</a></p>

# 🌐 wepapp

**wepapp** is a web application developed using HTML, CSS, and JavaScript along with Node.js and Express. It serves both static and dynamic web pages such as `index.html`, `login.html`, and `resourcelist.html`.

---

## ✅ Overview

- **Frontend:** HTML, CSS, JavaScript  
- **Backend:** Node.js + Express

---

## ⚙️ Installation and Running the Project

### 1. Install Node.js  
It is recommended to use version 18 or higher.

### 2. Install dependencies  
```bash
npm install
```

### 3. Start the project
```bash
npm start
```

The system will be available at:
```bash
http://localhost:3000
```
---

## 🗂️ Project Structure
```bash

wepapp/
├── css/               # CSS files for styling the web pages
├── fonts/             # Fonts used in the system (if any)
├── images/            # Image assets
├── js/                # Client-side JavaScript files
├── node_modules/      # Libraries installed via npm
├── index.html         # Main homepage
├── login.html         # Login page
├── resourcelist.html  # Resource list page
├── server.js          # Express server file
├── package.json       # Dependencies and scripts
└── README.md          # Project description


```

---

## 🧪 การทดสอบเบื้องต้น

- Open your browser at ``` http://localhost:3000 ```
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

