from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime, timedelta

from pymongo import MongoClient
import re
import logging

# ฟังก์ชัน: sanitize ชื่อ collection
def sanitize_collection_name(name):
    return re.sub(r'[^\w]', '_', name)

# ฟังก์ชันหลัก: ดึงจาก source แล้ว save ไป destination
def fetch_and_save_mongo_data(resource_name, source_mongo_uri, source_db_name, dest_mongo_uri, dest_db_name, **kwargs):
    try:
        sanitized_resource_name = sanitize_collection_name(resource_name)
        
        logging.info(f"Start processing resource: {resource_name} (sanitized: {sanitized_resource_name})")

        # Connect to source MongoDB
        source_client = MongoClient(source_mongo_uri)
        source_db = source_client[source_db_name]
        source_collection = source_db[resource_name]
        
        documents = list(source_collection.find())


        # Push raw data to XCom (optional)
        # kwargs['ti'].xcom_push(key=f"{sanitized_resource_name}_data", value=documents)

        # Connect to destination MongoDB
        dest_client = MongoClient(dest_mongo_uri)
        dest_db = dest_client[dest_db_name]
        dest_collection = dest_db[resource_name]

        if documents:
            dest_collection.delete_many({})  # ล้างก่อน
            dest_collection.insert_many(documents)

        logging.info(f"✅ Successfully fetched {len(documents)} documents from {source_db_name}.{sanitized_resource_name} and saved to {dest_db_name}.{sanitized_resource_name}")

        log_client = MongoClient(dest_mongo_uri)  # เก็บ log ใน DB เดียวกับ destination
        log_db = log_client['NSSO_DB']
        log_collection = log_db['task_logs']      # ชื่อ collection สำหรับเก็บ log

        # หา HCODE, LOCATION จากชื่อ DB
        db_location_mapping = {
            "Bangkok_DB": ("13814", "Bangkok"),
            "KhonKaen_DB": ("10670", "KhonKaen"),
            "Chonburi_DB": ("10662", "Chonburi")
        }
        hcode, location = db_location_mapping.get(source_db_name, ("00000", "Unknown"))

        # เก็บ log
        filter_criteria = {
            "hcode": hcode,
            "resource": resource_name.replace(" File", ""),  # ตัดคำว่า File ออก
            "location": location
        }

        update_doc = {
            "$set": {
                "status": "success",
                "update_date": datetime.utcnow()
            },
            "$setOnInsert": {
                "date_in": datetime.utcnow()
            }
        }

        log_collection.update_one(filter_criteria, update_doc, upsert=True)

        return len(documents)


    except Exception as e:
        logging.error(f"❌ Error processing {resource_name} from {source_db_name}: {e}")
        raise

# กำหนด DAG
dag = DAG(
    'mongo_fhir_data_fetcher_and_saver_multiple_dbs',
    description='Fetch FHIR-like resources from multiple MongoDB clusters and save into a single cluster by DB',
    schedule_interval=None,
    start_date=datetime(2025, 4, 7),
    catchup=False,
    tags=["MongoDB", "FHIR"],
)

# รายชื่อ resource (collection)
resources = [
    'Patient File',
    'Encounter File',
    'ServiceRequest File',
    'Claim File',
    'Condition File',
    'Procedure File',
    'Observation File',
    'MedicationRequest File'
]

# Source MongoDB URIs และ DB Names
mongo_connections = [
    {
        "mongo_uri": "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/",
        "db_name": "Bangkok_DB"
    },
    {
        "mongo_uri": "mongodb+srv://KhonKaenClusterAdmin:ruj03yiRTba66bhR@khonkaen-cluster.ndjzfcg.mongodb.net/",
        "db_name": "KhonKaen_DB"
    },
    {
        "mongo_uri": "mongodb+srv://ChonburiClustersAdmin:bthv9qvUiWLRd7QJ@chonburi-cluster.thrxexw.mongodb.net/",
        "db_name": "Chonburi_DB"
    }
]

# Destination MongoDB (NSSO_req)
destination_mongo_uri = "mongodb+srv://ClaimBridgeAdmin:Fl3zn0~G;A28iCL@claimbridge.dpfel8b.mongodb.net/"

# สร้าง Tasks
tasks = {}
for connection in mongo_connections:
    for i, resource in enumerate(resources):
        task_id = f'fetch_and_save_{sanitize_collection_name(resource.lower())}_{connection["db_name"]}_{i+1}'
        tasks[task_id] = PythonOperator(
            task_id=task_id,
            python_callable=fetch_and_save_mongo_data,
            op_args=[
                resource,
                connection["mongo_uri"],
                connection["db_name"],
                destination_mongo_uri,
                connection["db_name"]
            ],
            provide_context=True,
            retries=3,                     # ✅ retry 3 รอบ ถ้า error
            retry_delay=timedelta(minutes=5),
            dag=dag,
        )

# กำหนดลำดับการทำงาน (Sequential per DB)
for i in range(len(mongo_connections)):
    for j in range(len(resources) - 1):
        task_id_1 = f'fetch_and_save_{sanitize_collection_name(resources[j].lower())}_{mongo_connections[i]["db_name"]}_{j+1}'
        task_id_2 = f'fetch_and_save_{sanitize_collection_name(resources[j+1].lower())}_{mongo_connections[i]["db_name"]}_{j+2}'
        tasks[task_id_1] >> tasks[task_id_2]
