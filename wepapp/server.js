const express = require('express');
const path = require('path');
const { MongoClient } = require('mongodb');
const cors = require('cors');


const app = express();
const PORT = 3000;

// MongoDB setup
const mongoUrl = 'mongodb+srv://ClaimBridgeAdmin:Fl3zn0~G%3BA28iCL@claimbridge.dpfel8b.mongodb.net/';
const client = new MongoClient(mongoUrl);
let dbConnected = false;

async function connectDB() {
    if (!dbConnected) {
        await client.connect();
        dbConnected = true;
        console.log('✅ Connected to MongoDB');
    }
}

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname)));
app.use(express.static('public'));

// ฟังก์ชันช่วยเหลือในการดึงข้อมูล doctorId จาก Practitioner
const extractDoctorId = async (practitionerRef, client) => {
    if (!practitionerRef) return "N/A";

    const practitionerId = practitionerRef.split('/')[1];  // ดึง practitionerId ออกจาก reference
    const nssoDB = client.db("NSSO_DB");
    const practitionerCollection = nssoDB.collection("Practitioner File");

    // ค้นหาข้อมูล Practitioner ด้วย id
    const practitioner = await practitionerCollection.findOne({ id: practitionerId });

    
    console.log("Pracitioner Data:", practitioner); // แสดงข้อมูล practitioner ที่ค้นหา

    if (practitioner) {
        // ค้นหาค่าของ doctorId ที่มี system = 'IDThaiDoctor'
        const doctorIdentifier = practitioner.identifier.find(
            ident => ident.system === 'https://terms.sil-th.org/core/NamingSystem/IDThaiDoctor'
        );
        console.log("Doctor Identifier:", doctorIdentifier); // แสดงค่าของ doctorIdentifier ที่ได้

        return doctorIdentifier ? doctorIdentifier.value : "N/A";
    }

    return "N/A"; // หากไม่พบ practitioner
};


// ฟังก์ชันช่วยเหลือในการดึงข้อมูล AN จาก encounter
const extractAN = (encounter) => {
    let an = "N/A";
    encounter.identifier?.forEach(entry => {
        if (entry.system && entry.system.includes('an')) {
            an = entry.value || "N/A";
        }
    });
    return an;
};

// ฟังก์ชันช่วยเหลือในการดึงข้อมูล HN และ CID จาก Patient
const extractHNAndCID = async (patientId, db) => {
    const patientCollection = db.collection("Patient File");
    const patient = await patientCollection.findOne({ "id": patientId });

    if (patient) {
        let hn = "N/A";
        let cid = "N/A";
        patient.identifier?.forEach(entry => {
            if (entry.system && entry.system.toLowerCase().includes('hn')) {
                hn = entry.value || "N/A";
            }
            if (entry.system && entry.system.includes('cid')) {
                cid = entry.value || "N/A";
            }
        });
        return { hn, cid };
    }
    return { hn: "N/A", cid: "N/A" };
};

// API: Search MongoDB
app.post('/search', async (req, res) => {
    const { keyword, resources = [], locations = [] } = req.body;

    // ตรวจสอบค่าที่รับมา
    console.log("Locations:", locations);
    console.log("Resources:", resources);

    if (!locations || !resources) {
        return res.status(400).send("Location and resource must be provided");
    }

    try {
        await connectDB();
        const allResults = [];

        for (const location of locations) {
            const db = client.db(location);

            for (const resource of resources) {
                const collection = db.collection(resource);

                const query = keyword
                    ? { $text: { $search: keyword } }
                    : {};

                const results = await collection.find(query).toArray();

                // เพิ่มฟิลด์ระบุว่า query มาจากไหน
                const withMeta = results.map(doc => ({
                    ...doc,
                    _location: location,
                    _resource: resource
                }));

                allResults.push(...withMeta);
            }
        }

        // ตั้งค่าให้ส่งข้อมูลในรูปแบบ UTF-8
        res.setHeader('Content-Type', 'application/json; charset=utf-8');
        res.json(allResults);
    } catch (error) {
        console.error("❌ Server error:", error);
        res.status(500).send('Error connecting to database');
    }
});

// API: Encounter Search
app.get('/api/encounter/:id', async (req, res) => {
    const encounterId = req.params.id;
    const locationQuery = req.query.locations;
    const locations = locationQuery ? locationQuery.split(',') : [];

    if (locations.length === 0) {
        return res.status(400).json({ error: "Missing locations" });
    }

    try {
        await connectDB();
        console.log('✅ เชื่อมต่อฐานข้อมูลสำเร็จ');
        const resultList = [];

        for (const dbName of locations) {
            const db = client.db(dbName);
            console.log(`📡 กำลังค้นหาในฐานข้อมูล: ${dbName}`);

            const encounterCollection = db.collection("Encounter File");
            const encounter = await encounterCollection.findOne({ "id": encounterId });

            if (encounter) {
                console.log(`✅ พบ encounter ID ${encounterId} ใน ${dbName}:`, encounter);

                const an = extractAN(encounter);
                const patientId = encounter.subject?.reference?.split('/')[1];
                const { hn, cid } = await extractHNAndCID(patientId, db);

                console.log(`📑 พบ AN: ${an}`);
                console.log(`📑 พบ HN: ${hn}`);
                console.log(`📑 พบ CID: ${cid}`);

                // 🔍 ค้นหา MedicationRequest เพื่อดึง practitionerRef
                const medicationCollection = db.collection("MedicationRequest File");
                const medications = await medicationCollection.find({
                    "encounter.reference": `Encounter/${encounterId}`
                }).toArray();

                const firstMedWithRequester = medications.find(m => m.requester?.reference);
                const practitionerRef = firstMedWithRequester?.requester?.reference;

                console.log(`📑 พบ practitionerRef: ${practitionerRef}`);

                const doctorId = await extractDoctorId(practitionerRef, client);

                resultList.push({
                    location: dbName,
                    encounterId: encounterId,
                    an: an,
                    hn: hn,
                    cid: cid,
                    doctorId: doctorId
                });
            } else {
                console.log(`⚠️ ไม่พบ encounter ที่มี ID ${encounterId} ใน ${dbName}`);
            }
        }

        if (resultList.length === 0) {
            console.log("⚠️ ไม่พบ encounter ในฐานข้อมูลใดๆ");
            return res.status(404).json({ message: "ไม่พบ encounter ในฐานข้อมูลใดๆ" });
        }

        console.log("✅ ข้อมูล encounter ถูกส่งไปยัง client:", resultList);
        return res.json(resultList);
    } catch (error) {
        console.error("❌ เกิดข้อผิดพลาดในเซิร์ฟเวอร์:", error);
        return res.status(500).send("ข้อผิดพลาดของเซิร์ฟเวอร์");
    }
});


// --- เพิ่ม /task-logs ---
app.get('/task-logs', async (req, res) => {
    try {
        await connectDB(); // ใช้ connectDB ที่มีอยู่แล้ว

        const database = client.db("NSSO_DB");
        const collection = database.collection("task_logs");

        const taskLogs = await collection.find({}).toArray();
        res.json(taskLogs);
    } catch (err) {
        console.error("❌ Failed to fetch task logs:", err);
        res.status(500).json({ error: "Failed to fetch task logs" });
    }
});

// หน้าเว็บหลัก
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'index.html'));
});

// เริ่มเซิร์ฟเวอร์
app.listen(PORT, async () => {
    await connectDB();
    console.log(`🚀 Server is running at http://localhost:${PORT}`);
});
