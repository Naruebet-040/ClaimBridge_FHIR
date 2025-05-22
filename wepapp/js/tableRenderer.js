// Function to reset the table (clear existing rows)
function resetTable() {
    const tableBody = document.getElementById('table-body');
    if (tableBody) {
        tableBody.innerHTML = ''; // Clear all rows
    }
}

// Function to map JSON data to an HTML table
function mapJsonToTable(jsonData, selectedLocations) {
    const tableBody = document.getElementById('table-body');

    if (!tableBody) {
        console.error("❌ Table body element with ID 'table-body' not found!");
        return;
    }

    console.log("📊 Data to display in table:", jsonData);

    if (!jsonData || jsonData.length === 0) {
        console.log("❌ No data available to display in table.");
        return;
    }

    jsonData.forEach(item => {
        const row = document.createElement('tr');

        // Extract subjectId from the encounter reference
        const subjectReference = item.encounter?.reference;
        const subjectId = subjectReference ? subjectReference.split('/')[1] : null;
        console.log('Subject ID:', subjectId);

        // Create cells for Hcode, HN, AN, CID, etc.
        const hcodeCell = document.createElement('td');
        hcodeCell.innerHTML = `<b>${item.identifier[0].value}</b>` || "N/A";
        row.appendChild(hcodeCell);

        const hnCell = document.createElement('td');
        hnCell.innerText = "Loading...";
        row.appendChild(hnCell);

        const anCell = document.createElement('td');
        anCell.innerText = "Loading...";
        row.appendChild(anCell);

        const cidCell = document.createElement('td');
        cidCell.innerText = "Loading...";
        row.appendChild(cidCell);

        const dateServCell = document.createElement('td');
        dateServCell.innerText = item.authoredOn ? formatDateToDDMMYYYY(item.authoredOn) : "N/A";
        row.appendChild(dateServCell);

        const didCell = document.createElement('td');
        didCell.innerText = item.medicationCodeableConcept?.coding[0]?.code || "N/A";
        row.appendChild(didCell);

        const didnameCell = document.createElement('td');
        didnameCell.innerText = item.medicationCodeableConcept?.coding[0]?.display || "N/A";
        row.appendChild(didnameCell);

        const amountCell = document.createElement('td');
        amountCell.innerText = item.extension?.[0]?.valueString || "N/A";
        row.appendChild(amountCell);

        const unitCell = document.createElement('td');
        unitCell.innerText = item.extension?.[2]?.valueString || "N/A";
        row.appendChild(unitCell);

        const providerCell = document.createElement('td');
        providerCell.innerText = "Loading...";
        row.appendChild(providerCell);

        tableBody.appendChild(row);


        // Fetch AN, HN, CID, Provider data
        if (subjectId && item._location) {
            console.log("Fetching data for subjectId:", subjectId, "from location:", item._location);

            fetch(`/api/encounter/${subjectId}?locations=${item._location}`)
                .then(res => res.json())
                .then(data => {
                    if (data.length > 0) {
                        const locationData = data[0];
                        console.log("Received data:", locationData);
                        console.log("AN Value:", locationData.an);  

                        // Update AN, HN, CID values
                        anCell.innerText = locationData.an || "N/A";
                        hnCell.innerText = locationData.hn || "N/A";
                        cidCell.innerText = locationData.cid || "N/A";
                        providerCell.innerText = "ว." + locationData.doctorId || "N/A";
                    } else {
                        console.log("No data found for subjectId:", subjectId);
                        anCell.innerText = "N/A";
                        hnCell.innerText = "N/A";
                        cidCell.innerText = "N/A";
                        providerCell.innerText = "N/A";
                    }
                })
                .catch(error => {
                    console.error('❌ Error fetching CID/HN/AN:', error);
                    anCell.innerText = "Error";
                });
        } else {
            console.log("Missing subjectId or location");
            anCell.innerText = "N/A";
        }
    });
}
