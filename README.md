# Collage Exam System (IntelliGen) 🧠🎓
**Next-Generation Multi-Tenant University & Exam Management System**

IntelliGen is a high-performance, role-based educational platform powered by **Spring Boot** and **AI/ML (Llama 3)**. It provides strict multi-tenant data isolation, hierarchical staff approval workflows, and an AI-driven exam generation pipeline that instantly creates assessments from uploaded PDF notes.

---

## 🌟 Key Features

* **Strict Multi-Tenant Isolation:** Dynamically separates databases and operational metrics by `College` and `Department Branch`. An HOD from Computer Science can never access data from Mechanical Engineering.
* **AI-Powered Exam Engine (Llama 3):** Teachers and HODs can upload PDF lecture notes. The built-in AI pipeline extracts text and utilizes local/cloud Llama 3 models to instantly generate structured Multiple-Choice Questions (MCQs).
* **Hierarchical Role Approvals (RBAC):**
  * **Admin:** Registers Colleges and approves Principals.
  * **Principal:** Oversees the college and approves HODs.
  * **HOD (Head of Department):** Manages branch tracking, creates branch exams, and approves Teachers.
  * **Teacher:** Creates subject-specific exams and monitors student analytics.
  * **Student:** Takes live online assessments.
* **Automated Term Management:** Secure HOD/Principal engines to increment live semester parameters for thousands of students at once without risking historical grade registries.

---

## 📁 Repository Structure

* **`springboot_backend/`**: Contains the core Java 25 / Spring Boot 4.x application, REST APIs, Security Configurations, and Database Entities.
* **`question_generator_llama3/`**: Contains the dedicated AI microservice/scripts for processing PDFs and generating MCQs using Llama 3.

---

## 🛠️ Tech Stack

**Backend System**
* **Java 25 / Spring Boot 4.x** (Core Framework)
* **Spring Security & JWT** (Authentication & Multi-Tenant Interceptors)
* **Hibernate / Spring Data JPA** (Database ORM)
* **MySQL** (Relational Database)
* **Apache PDFBox** (Document Parsing)
* **Spring Boot Virtual Threads** (High-Concurrency / Low-Latency I/O)

**AI & Microservices**
* **Llama 3** (Question Generation Engine)

**Frontend System**
* **HTML5 / CSS3 / JavaScript** (Vanilla Native Forms)
* **Tailwind CSS** (Rapid Utility Styling)
* **FontAwesome** (Iconography)

---

## 🚀 Getting Started

### Prerequisites
Before you begin, ensure you have the following installed on your local machine:
* [Java Development Kit (JDK) 25+](https://www.oracle.com/in/java/technologies/downloads/#java25)
* [MySQL Server](https://dev.mysql.com/downloads/installer/) (Running locally)
* Python 3.x (For the Llama 3 generator)
* Git

### Installation & Deployment

**1. Clone the repository**
```bash
git clone [https://github.com/MayankMahajan-0611/collage_exam_system.git](https://github.com/MayankMahajan-0611/collage_exam_system.git)
cd collage_exam_system

```

**2. Configure the Database**
Open your MySQL workbench or terminal and create a fresh database for the application:

```sql
CREATE DATABASE intelligen_db;

```

Next, open `springboot_backend/src/main/resources/application.properties` and update your database credentials:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/intelligen_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=your_mysql_password

# Hibernate auto-creates the tables on first run
spring.jpa.hibernate.ddl-auto=update

```

**3. Run the Spring Boot Backend**
Navigate into the backend directory and use the Maven wrapper to cleanly compile and run the application:

*For Windows (PowerShell/CMD):*

```powershell
cd springboot_backend
.\mvnw clean spring-boot:run

```

*For Mac/Linux:*

```bash
cd springboot_backend
./mvnw clean spring-boot:run

```

*The backend will boot up and bind to `http://localhost:8080`.*

**4. Run the AI Microservice**
Navigate to the `question_generator_llama3` directory and start the AI question generation service (refer to specific instructions inside that folder if applicable).

**5. Run the Frontend UIs**
Because the frontend is built with vanilla HTML/JS, no build step is required.

* Open the frontend folder containing your `.html` files.
* You can open `login.html` directly in your browser by double-clicking it.
* *(Recommended)*: Use a local development server like the VS Code **Live Server** extension to serve the files properly.

**6. Initial Admin Setup (Bootstrap the System)**
To establish the first administrative account, you must register a user through the frontend and manually elevate their privileges in the database.

1. Open the frontend registration page (`register.html`) and create a new Teacher account (e.g., `admin@igen.com`).
2. Open your MySQL client or terminal and connect to the `intelligen_db` database.
3. Execute the following SQL query to manually approve the account and set the branch to `ADMIN`:

```sql
UPDATE teachers 
SET is_approved = TRUE, branch_name = 'ADMIN', is_principal = FALSE 
WHERE username = 'admin@igen.com' AND id = 1;

```

---

## 📡 Complete API Endpoint Directory

### 🔐 Authentication & Onboarding

| Endpoint | Method | Role | Description |
| --- | --- | --- | --- |
| `/api/auth/login` | `POST` | ALL | Authenticates user credentials and returns role/tenant mapping. |
| `/api/auth/register` | `POST` | ALL | Registers a new user account (Pending approval based on role). |

### 🛠️ System Admin Operations

| Endpoint | Method | Role | Description |
| --- | --- | --- | --- |
| `/api/admin/colleges` | `POST` | ADMIN | Registers a new college entity into the multi-tenant system. |
| `/api/admin/principals/pending` | `GET` | ADMIN | Retrieves a list of newly registered principals waiting for approval. |
| `/api/admin/principals/approve/{id}` | `PUT` | ADMIN | Approves a principal's account to activate their college dashboard. |

### 🏛️ Principal Operations

| Endpoint | Method | Role | Description |
| --- | --- | --- | --- |
| `/api/principal/hods/pending` | `GET` | PRINCIPAL | Retrieves a list of unapproved HODs for their specific college. |
| `/api/principal/hods/approve/{id}` | `PUT` | PRINCIPAL | Approves an HOD to manage a specific branch/department. |

### 📂 HOD (Head of Department) Operations

| Endpoint | Method | Role | Description |
| --- | --- | --- | --- |
| `/api/hod/staff/pending` | `GET` | HOD | Retrieves unapproved teachers attempting to join the HOD's branch. |
| `/api/hod/staff/active` | `GET` | HOD | Retrieves all active, approved teachers within the branch. |
| `/api/hod/staff/approve/{id}` | `PUT` | HOD | Approves a specific teacher's account for branch access. |
| `/api/hod/exams` | `GET` | HOD | Retrieves all exams published within the department. |
| `/api/hod/exams/save` | `POST` | HOD | Publishes a manually configured or AI-generated exam to the branch. |
| `/api/hod/upload-notes` | `POST` | HOD | Uploads PDF, parses text, and calls Llama 3 AI for MCQ generation. |
| `/api/hod/increment-semester` | `POST` | HOD | Mass advances the semester tracker for all branch students (+1 Step). |

### 📝 Teacher Operations

| Endpoint | Method | Role | Description |
| --- | --- | --- | --- |
| `/api/teacher/exams` | `GET` | TEACHER | Retrieves all exams created by this specific teacher. |
| `/api/teacher/exams/save` | `POST` | TEACHER | Publishes an exam exclusively to the teacher's mapped classes. |
| `/api/teacher/upload-notes` | `POST` | TEACHER | Uploads PDF and triggers Llama 3 AI for automated question generation. |

### 🎓 Student Operations

| Endpoint | Method | Role | Description |
| --- | --- | --- | --- |
| `/api/student/exams/active` | `GET` | STUDENT | Retrieves a list of live/active exams available for the student's semester. |
| `/api/student/exams/submit` | `POST` | STUDENT | Submits the completed exam answers and instantly calculates the grade. |

---
*Built with ❤️ for Modern Educational Infrastructure.*
