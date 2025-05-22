from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime
from pymongo import MongoClient

# ฟังก์ชันดึงข้อมูลจาก MongoDB หลายๆ ตัว
def fetch_mongo_data_from_multiple_dbs(resource_name, mongo_uri, db_name, **kwargs):
    try:
        client = MongoClient(mongo_uri)
        db = client[db_name]
        
        # ดึง collection ตาม resource_name
        collection = db[resource_name]
        
        documents = list(collection.find())
        kwargs['ti'].xcom_push(key=f"{resource_name}_data", value=documents)
        return documents
    except Exception as e:
        raise Exception(f"Error fetching {resource_name} from {db_name}: {e}")

# กำหนด DAG
dag = DAG(
    'mongo_fhir_data_fetcher_multiple_dbs',
    description='Fetch FHIR-like resources from multiple MongoDB instances',
    schedule_interval=None,
    start_date=datetime(2025, 4, 7),
    catchup=False,
)

# รายชื่อ resource (ชื่อ collection)
resources = [
    'Patient', 
    'Encounter', 
    'ServiceRequest', 
    'Claim', 
    'Condition', 
    'Procedure', 
    'Observation', 
    'MedicationRequest'
]

# รายชื่อ MongoDB URI และ Database ที่ต้องการดึงข้อมูล
mongo_connections = [
    {
        "mongo_uri": "mongodb+srv://ClaimBridgeAdmin:Fl3zn0~G;A28iCL@claimbridge.dpfel8b.mongodb.net/",
        "db_name": "FHIR_Resource"
    },
    {
        "mongo_uri": "mongodb+srv://ClaimBridgeAdmin:Fl3zn0~G;A28iCL@claimbridge.dpfel8b.mongodb.net/",
        "db_name": "KhonKaen_DB"
    },
    {
        "mongo_uri": "mongodb+srv://ClaimBridgeAdmin:Fl3zn0~G;A28iCL@claimbridge.dpfel8b.mongodb.net/",
        "db_name": "Chonburi_DB"
    }
]

# สร้าง task สำหรับแต่ละ resource และ MongoDB connection
tasks = {}
for connection in mongo_connections:
    for resource in resources:
        task_id = f'fetch_{resource.lower()}_{connection["db_name"]}'
        tasks[task_id] = PythonOperator(
            task_id=task_id,  # task_id ใช้ตัวเล็ก
            python_callable=fetch_mongo_data_from_multiple_dbs,
            op_args=[resource, connection["mongo_uri"], connection["db_name"]],
            dag=dag,
        )

# ตัวอย่างการเชื่อมโยง task ต่างๆ
# กำหนดลำดับการรัน Task ต่อๆ กัน
for i in range(len(mongo_connections) - 1):
    for j in range(len(resources) - 1):
        tasks[f'fetch_{resources[j].lower()}_{mongo_connections[i]["db_name"]}'] >> tasks[f'fetch_{resources[j+1].lower()}_{mongo_connections[i+1]["db_name"]}']

