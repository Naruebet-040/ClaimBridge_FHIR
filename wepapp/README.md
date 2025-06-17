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
- Verify that ``` index.html ``` loads correctly
- Try accessing the Sign in page with credentials:
  ```bash
  username: NSSO
  password: NSSOpassword
  ```
- Navigate to the Resource List page
- Check that CSS and JS files load and work properly

---

## 📌 Notes
- If you need to connect additional APIs, you can customize ``` server.js ``` accordingly
- For future growth, consider migrating to React, Vue, or other frameworks to better manage more complex UI

---

> Maintained by ClaimBridge FHIR

