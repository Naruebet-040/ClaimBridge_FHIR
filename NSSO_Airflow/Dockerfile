FROM apache/airflow:2.5.1

USER root

# ติดตั้ง Java และ Allure CLI
RUN apt-get update && \
    apt-get install -y openjdk-11-jre wget unzip && \
    wget https://github.com/allure-framework/allure2/releases/download/2.27.0/allure-2.27.0.zip && \
    unzip allure-2.27.0.zip -d /opt/ && \
    ln -s /opt/allure-2.27.0/bin/allure /usr/bin/allure && \
    rm allure-2.27.0.zip

USER airflow
