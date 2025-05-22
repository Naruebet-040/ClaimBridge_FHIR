import logging
from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime

# ฟังก์ชันที่ใช้แสดงข้อมูลใน Log
def log_task_trigger(context):
    task_id = context['task_instance'].task_id
    dag_id = context['dag'].dag_id
    execution_date = context['execution_date']
    status = context['task_instance'].state
    
    # ใช้ logging แสดงข้อมูลใน Log
    logging.info(f"Task triggered: {task_id}")
    logging.info(f"DAG ID: {dag_id}")
    logging.info(f"Execution Date: {execution_date}")
    logging.info(f"Task Status: {status}")
    logging.info(f"Timestamp: {datetime.now()}")

# กำหนด DAG ของคุณ
dag = DAG(
    'example_dag',  # ชื่อ DAG ของคุณ
    schedule_interval='@daily',  # การตั้งเวลา
    start_date=datetime(2025, 1, 1),
    catchup=False
)

# ฟังก์ชันที่จะรันใน Task ของคุณ
def your_function():
    logging.info("This is the task function.")

# PythonOperator สำหรับ Task ที่ต้องการ
task = PythonOperator(
    task_id='your_task',
    python_callable=your_function,  # ฟังก์ชันที่จะรัน
    on_success_callback=log_task_trigger,  # เรียกใช้ callback เมื่อ Task สำเร็จ
    on_failure_callback=log_task_trigger,  # เรียกใช้ callback เมื่อ Task ล้มเหลว
    dag=dag,
)
