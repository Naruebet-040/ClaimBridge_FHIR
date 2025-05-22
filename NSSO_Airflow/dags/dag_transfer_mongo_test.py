from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime
from pymongo import MongoClient

# ฟังก์ชันดึงข้อมูลจาก MongoDB
def fetch_mongo_data(resource_name, **kwargs):
    mongo_uri = "mongodb+srv://ClaimBridgeAdmin:Fl3zn0~G;A28iCL@claimbridge.dpfel8b.mongodb.net/"  # URL ของ MongoDB ของคุณ
    db_name = "FHIR_Resource"            # ชื่อ Database ของคุณ
    
    try:
        client = MongoClient(mongo_uri)
        db = client[db_name]
        
        # ดึง collection ตาม resource_name
        collection = db[resource_name]
        
        documents = list(collection.find())
        kwargs['ti'].xcom_push(key=f"{resource_name}_data", value=documents)
        return documents
    except Exception as e:
        raise Exception(f"Error fetching {resource_name}: {e}")

# กำหนด DAG
dag = DAG(
    'mongo_fhir_data_fetcher',
    description='Fetch FHIR-like resources from MongoDB',
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

# สร้าง task สำหรับแต่ละ resource
tasks = {}
for resource in resources:
    tasks[resource] = PythonOperator(
        task_id=f'fetch_{resource.lower()}',  # task_id ใช้ตัวเล็ก
        python_callable=fetch_mongo_data,
        op_args=[resource],
        dag=dag,
    )

# กำหนดลำดับการรัน Task ต่อๆ กัน
for i in range(len(resources) - 1):
    tasks[resources[i]] >> tasks[resources[i + 1]]