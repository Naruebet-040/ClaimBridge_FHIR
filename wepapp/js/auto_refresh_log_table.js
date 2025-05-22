// auto_refresh__log_table.js (Client-side code)
async function fetchTaskLogs() {
    try {
        const response = await fetch('/task-logs');
        const taskLogs = await response.json();

        if (taskLogs.length === 0) {
            console.log("No data found in the collection.");
        }

        const tbody = document.getElementById("task-body");
        tbody.innerHTML = "";

        taskLogs.forEach(task => {
            const row = document.createElement("tr");
        
            // เช็ก status เพื่อเลือกสีจุด
            const status = (task.status || '').toLowerCase();
            let statusClass = '';
            if (status === 'moving') statusClass = 'moving-dot';
            else if (status === 'pending') statusClass = 'pending-dot';
            else if (status === 'cancelled') statusClass = 'cancelled-dot';
            else if (status === 'success') statusClass = 'success-dot';

            // แสดงเฉพาะวันที่ (ใช้ toLocaleDateString)
            const dateIn = task.date_in ? formatDateToDDMMYYYY(new Date(task.date_in).toLocaleDateString()) : "-";
            const updateDate = task.update_date ? formatDateToDDMMYYYY(new Date(task.update_date).toLocaleDateString()) : "-";

        
            // ทำจุดหน้าสถานะ
            const statusHTML = statusClass
                ? `<span class="status-dot ${statusClass}"></span> ${task.status || "-"}`
                : (task.status || "-");
        
            row.innerHTML = `
                <td>${task.hcode || "-"}</td>
                <td>${statusHTML}</td> <!-- ใส่จุดที่นี่ -->
                <td>${task.resource || "-"}</td>
                <td>${task.location || "-"}</td>
                <td>${dateIn}</td>
                <td>${updateDate}</td>
            `;
            tbody.appendChild(row);
        });
        
    } catch (err) {
        console.error("❌ Failed to fetch task logs:", err);
    }
}

// Fetch data when the page loads
fetchTaskLogs();

// Auto-refresh every 30 seconds
setInterval(fetchTaskLogs, 30000);


// Fetch data when the page loads
fetchTaskLogs();

// Auto-refresh every 30 seconds
setInterval(fetchTaskLogs, 30000);
