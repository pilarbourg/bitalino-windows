<a id="readme-top"></a>

<br />
<div align="center">
   <a href="https://github.com/alejandraoshea/sma-server">
     <img src="logo.ico" alt="SMA Server Logo" width="80" height="80">
   </a>
  <h3 align="center">SMA Server</h3>

  <p align="center">
    Signal obtention application which allows to record EMG and ECG signlas thorugh a BITalino device and download them into a directory in your local computer.
      

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li><a href="#about-the-project">About The Application</a></li>
    <li><a href="#built-with">Built With</a></li>
    <li><a href="#getting-started">Getting Started</a></li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#acknowledgments">Acknowledgments</a></li>
  </ol>
</details>

## About The Application

 BITALINO-WINDOWS is an application designed to obtain a TXT file with a signal recording along with the sample rate. It supports:

- BITalino connection with the computer
- Sampling rate selection (10, 100 or 1000)
- Selection of the signal type to record (ECG or EMG)
- Stopping the signal acquisition when suitable or waiting for the maximum recording time to finish the recording (2 minutes)
- Reporting system for doctors

<p align="right">(<a href="#readme-top">back to top</a>)</p>

### Built With

* Java
* Spring Boot
* PostgreSQL

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Getting Started

Follow these steps to set up a local development environment.

### Prerequisites

- Java 17+
- Maven
- PostgreSQL
- PostgreSQL Database

### Installation

1. Clone the repository:
   ```sh
   git clone https://github.com/alejandraoshea/sma-server.git
   cd sma-server

2. Configure your database in application-local.yml:
   ```yaml
   spring:
     config:
       activate:
         on-profile: local
   
     datasource:
       url: jdbc:postgresql://localhost:5432/telemedicine_local
       driver-class-name: org.postgresql.Driver
       hikari:
         schema: public
   
   server:
     port: 8443
     ssl:
       enabled: true
       key-store: /path/to/your/keystore.p12
       key-store-password: YOUR_KEYSTORE_PASSWORD
       key-store-type: PKCS12
       key-alias: YOUR_KEY_ALIAS
   
   admin:
     username: ADMIN_USERNAME
     password: ADMIN_PASSWORD
   
   operator:
     username: OPERATOR_USERNAME
     password: OPERATOR_PASSWORD
   
   jwt:
     secret: JWT_SECRET
     expiration: 3600000

3. Build the project:
   ```sh
   mvn clean install

4. Start the server:
   ```sh
   mvn clean package
   scripts/start-server.sh
   Username: ADMIN_USERNAME
   Password: ADMIN_PASSWORD

5. Stop the server:
   ```sh
   scripts/stop-server.sh
   Username: ADMIN_USERNAME
   Password: ADMIN_PASSWORD

<p align="right">(<a href="#readme-top">back to top</a>)</p>


Usage

* API endpoints for patients, doctors, sessions, and signals are exposed via REST.
* Upload ECG/EMG files to a session to store signals.
* Generate report summaries for patient ECG/EMG measurement sessions.
* Authentication & authorization (JWT)
* Implementation of advanced signal analysis
* Integration of notification system for doctor approvals

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<p align="right">(<a href="#readme-top">back to top</a>)</p>

License

Distributed under the MIT License.

<p align="right">(<a href="#readme-top">back to top</a>)</p>


Acknowledgments

* Best README Template
* Contrib.rocks for contributors graph
* Spring Boot Documentation

<p align="right">(<a href="#readme-top">back to top</a>)</p>

