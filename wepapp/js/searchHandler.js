document.getElementById('search-btn').addEventListener('click', async function() {
    const keyword = document.getElementById('keyword').value.toLowerCase();  // ดึงค่าจากช่องกรอกคำและแปลงเป็นตัวพิมพ์เล็ก
    const rows = document.querySelectorAll('#table-body tr');  // ดึงทุกแถวในตาราง

    // ตรวจสอบทุกแถวเพื่อหาคำค้นหาที่ตรงกับข้อมูลในแถว
    rows.forEach(row => {
        const cells = row.querySelectorAll('td');  // ดึงเซลล์ทั้งหมดในแถว
        let rowText = '';  // สร้างตัวแปรสำหรับเก็บข้อความในแถว

        // รวมข้อมูลจากทุกเซลล์ของแถวเป็นข้อความ
        cells.forEach(cell => {
            rowText += cell.textContent.toLowerCase();  // แปลงข้อความในเซลล์เป็นตัวพิมพ์เล็ก
        });

        // ถ้าคำค้นหามีในแถว ให้แสดงแถวไว้ ถ้าไม่ตรง ให้ซ่อนแถวนั้น
        if (rowText.includes(keyword)) {
            row.style.display = '';  // แสดงแถว
        } else {
            row.style.display = 'none';  // ซ่อนแถว
        }
    });

    

    // Get selected resources
    //const resources = Array.from(document.querySelectorAll('input[type="checkbox"][id^="resource"]:checked'))
    //                        .map(checkbox => checkbox.value);
    //console.log("📦 Selected Resources:", resources);
    const selectElement = document.getElementById('resource-select');
    const resources = Array.from(selectElement.selectedOptions).map(option => option.value);
    
    if (!resources || resources === "เลือก Resource") {
        alert("กรุณาเลือก Resource ก่อนค้นหา");
        return; // ไม่ส่ง request ถ้าไม่ได้เลือก
    }
    console.log("📦 Selected Resource:", resources);

    
    // Get selected locations
    const locations = Array.from(document.querySelectorAll('input[type="checkbox"][id^="location"]:checked'))
                            .map(checkbox => checkbox.value);
    console.log("📍 Selected Locations:", locations);


    const requestData = {
        keyword: keyword,
        resources: resources,
        locations: locations
    };

    console.log("📤 Sending requestData:", requestData);

    try {
        const response = await fetch('/search', {
            method: 'POST',
            headers: {
            'Content-Type': 'application/json; charset=utf-8'
        },
        body: JSON.stringify(requestData)
    });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        console.log("✅ Response from server:", data);

        // Clear previous results
        const resultsContainer = document.getElementById('results');
        resultsContainer.innerHTML = '';

        // ✅ แสดงตาราง
        const table = document.querySelector('table');
        if (table) {
            table.style.display = 'table';
        }

        // รีเซ็ตข้อมูลในตาราง
        resetTable();

      // Call the function to map the JSON data to the table
        mapJsonToTable(data, locations);

    } catch (error) {
        console.error("❌ Fetch error:", error);
    }
});