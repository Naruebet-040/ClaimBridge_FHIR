import pytest
from pymongo import MongoClient

# ฟังก์ชันตัวอย่าง
def fetch_data(mongo_uri, db_name, resource_name):
    client = MongoClient(mongo_uri)
    db = client[db_name]
    collection = db[resource_name]
    documents = list(collection.find())
    return documents

# ใช้ parametrize อย่างถูกต้อง
@pytest.mark.parametrize("db_name, resource_name", [
    ("Bangkok_DB", "Patient"),
    ("Bangkok_DB", "Encounter"),
    ("Bangkok_DB", "ServiceRequest"),
    ("Bangkok_DB", "Claim"),
    ("Bangkok_DB", "Condition"),
    ("Bangkok_DB", "Procedure"),
    ("Bangkok_DB", "Observation"),
    ("Bangkok_DB", "MedicationRequest"),
    ("KhonKaen_DB", "Patient"),
    ("KhonKaen_DB", "Encounter"),
    ("KhonKaen_DB", "ServiceRequest"),
    ("KhonKaen_DB", "Claim"),
    ("KhonKaen_DB", "Condition"),
    ("KhonKaen_DB", "Procedure"),
    ("KhonKaen_DB", "Observation"),
    ("KhonKaen_DB", "MedicationRequest"),
    ("Chonburi_DB", "Patient"),
    ("Chonburi_DB", "Encounter"),
    ("Chonburi_DB", "ServiceRequest"),
    ("Chonburi_DB", "Claim"),
    ("Chonburi_DB", "Condition"),
    ("Chonburi_DB", "Procedure"),
    ("Chonburi_DB", "Observation"),
    ("Chonburi_DB", "MedicationRequest")
])
def test_fetch_documents(db_name, resource_name):
    mongo_uri = "mongodb+srv://ClaimBridgeAdmin:Fl3zn0%7EG%3BA28iCL@claimbridge.dpfel8b.mongodb.net/"
    
    docs = fetch_data(mongo_uri, db_name, resource_name)
    
    assert docs is not None, f"Documents for {resource_name} in {db_name} should not be None"
    assert isinstance(docs, list), f"Documents for {resource_name} in {db_name} should be a list"
    assert len(docs) >= 0, f"Documents for {resource_name} in {db_name} should be zero or more"
