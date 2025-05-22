import sys
import os

# เพิ่ม path เข้าไปให้หา dags เจอ
sys.path.append(os.path.join(os.path.dirname(__file__), '..'))

from dags.dag_transfer_mongo_3DB_Multitasks_to_NSSO import fetch_and_save_mongo_data

# Test parameter
resource_name = "Encounter File"

# เพิ่ม ?tls=true&tlsAllowInvalidCertificates=true เข้าไปป้องกัน SSL Error
source_mongo_uri = "mongodb+srv://BangkokClusterAdmin:mgl0vN79Qp9HxzP4@bangkok-cluster.wzbpyxu.mongodb.net/?tls=true&tlsAllowInvalidCertificates=true"
source_db_name = "Bangkok_DB"

dest_mongo_uri = "mongodb+srv://ClaimBridgeAdmin:Fl3zn0~G;A28iCL@claimbridge.dpfel8b.mongodb.net/?tls=true&tlsAllowInvalidCertificates=true"
dest_db_name = "Bangkok_DB"

# เรียกใช้ฟังก์ชัน
if __name__ == "__main__":
    try:
        count = fetch_and_save_mongo_data(
            resource_name,
            source_mongo_uri,
            source_db_name,
            dest_mongo_uri,
            dest_db_name
        )
        print(f"✅ Successfully processed {count} documents.")
    except Exception as e:
        print(f"❌ Error occurred: {e}")
