# Smart Segregation System
## AI-Powered Waste Management System
### Project Documentation

---

## 1. PROBLEM STATEMENT

Traditional waste management systems are **manual, inefficient, and reactive**. 
Workers physically check bins to see if they are full, recyclables get mixed with hazardous waste, 
and there is no real-time data on waste generation patterns or recycling rates.

### Core Problems
- Bins overflow before collection teams arrive
- Waste is not sorted correctly — recyclables go to landfill
- No data on how much waste is generated per zone or category
- High operational cost due to unnecessary pickup trips
- No way to track recycling performance or environmental impact

### Our Solution
An **AI-powered smart waste segregation and monitoring system** that:
- Automatically **identifies and classifies waste** using computer vision
- **Monitors bin fill levels** in real-time via IoT sensors
- **Alerts operators** before bins overflow
- **Schedules pickups** intelligently
- Provides **analytics and reports** on recycling rates and CO₂ saved

---

## 2. PROJECT OVERVIEW

| Field             | Details                                              |
|-------------------|------------------------------------------------------|
| Project Name      | Smart Segregation System                             |
| Version           | 2.0                                                  |
| Type              | Full Stack Web Application                           |
| Backend Language  | Java 21                                              |
| Frontend          | HTML5, CSS3, JavaScript                              |
| Database          | MySQL 8.0                                            |
| AI/ML             | TensorFlow.js COCO-SSD + Java keyword classifier     |
| Charts            | Chart.js v4.4                                        |
| Author            | G Dineep Chandra                                     |

---

## 3. TECH STACK

### Backend — Java
| Component            | Technology                        |
|----------------------|-----------------------------------|
| HTTP Server          | Java JDK `com.sun.net.httpserver` |
| REST API             | Custom Java handlers (13 routes)  |
| Database Driver      | MySQL JDBC Connector 8.0.33       |
| Auth                 | UUID session tokens               |
| Background Threads   | `ScheduledExecutorService`        |
| Concurrency          | `ConcurrentHashMap`               |
| Build                | `javac` (no Maven/Gradle needed)  |

### Frontend — Web Technologies
| Component      | Technology                  |
|----------------|-----------------------------|
| Markup         | HTML5 (Single Page App)     |
| Styling        | CSS3 (Flexbox, Grid, Animations) |
| Scripting      | JavaScript ES2022 (Fetch API, async/await) |
| Charts         | Chart.js v4.4               |
| Icons          | Font Awesome 6              |
| Fonts          | Google Fonts — Inter        |
| AI Detection   | TensorFlow.js COCO-SSD      |

### Database — MySQL
| Field         | Value                  |
|---------------|------------------------|
| Database Name | `auth_system`          |
| Table         | `users`                |
| Columns       | id, username, email, password, role, created_at, last_login |

---

## 4. SYSTEM ARCHITECTURE

```
┌─────────────────────────────────────────────────────────┐
│                    BROWSER (Client)                      │
│   HTML + CSS + JavaScript  (Single Page Application)     │
│                                                          │
│  login.html → signup.html → index.html (Dashboard)       │
│                      ↕  HTTP REST API                    │
├─────────────────────────────────────────────────────────┤
│                 JAVA BACKEND SERVER                       │
│              SmartSegServer.java (:3000)                 │
│                                                          │
│  ┌──────────────┐  ┌─────────────┐  ┌───────────────┐  │
│  │ Auth Module  │  │ Bin Manager │  │  AI Engine    │  │
│  │ Login/Signup │  │ IoT Sensors │  │ classifyWaste │  │
│  │ Session Mgmt │  │ Alerts Gen  │  │ 100+ keywords │  │
│  └──────────────┘  └─────────────┘  └───────────────┘  │
│                      ↕  JDBC                             │
├─────────────────────────────────────────────────────────┤
│              MySQL Database (auth_system)                 │
│              users table — stores accounts               │
└─────────────────────────────────────────────────────────┘
```

---

## 5. FEATURES & MODULES

### Module 1 — Authentication System
- **Login** with username + password (verified against MySQL)
- **Sign Up** — new accounts saved permanently to MySQL
- **Session tokens** (UUID-based) stored in localStorage
- **Remember me** functionality
- **Role-based access** — 6 roles supported
- MySQL `last_login` timestamp updated on every login
- Fallback to in-memory credentials if MySQL offline

### Module 2 — Dashboard
- **4 KPI cards** — Total Waste, Recycling Rate, Active Bins, CO₂ Saved
- **Pie chart** — Waste composition by category
- **Line chart** — Weekly segregation trend
- **Live bin status** list with fill % progress bars
- **Recent alerts** from Java backend
- **AI Insight** messages
- Auto-refreshes every 8 seconds from Java API

### Module 3 — AI Waste Detection (Upload Image)
- Upload any image → **TensorFlow.js COCO-SSD** detects objects
- Detected objects sent to **Java classification engine**
- Java maps to waste category (Organic/Recyclable/Hazardous/E-Waste/Residual)
- **Bounding boxes** drawn on image with labels
- **Manual keyword classify** — type "battery" → Java returns Hazardous
- **"No Waste Detected"** popup if image has no waste items
- Detection log with confidence scores and timestamps

### Module 4 — Bin Management
- **10 smart bins** across 5 facility zones (A–E)
- Real-time IoT data: **fill level, temperature, weight, status**
- Java background thread updates sensors every 10 seconds
- **Zone map** — click zones to filter bins
- Color-coded urgency: Green (normal) / Orange (warning) / Red (critical)
- **Schedule Pickup** — calls Java API, resets bin fill, increments counter
- **Maintenance log** — updates last service date in Java
- Auto-generates alerts when bin reaches 90%+

### Module 5 — AI Model Training
- Dataset overview: 9,797 training images, 5 categories
- **Accuracy chart** — weekly model performance
- Model architecture selector (MobileNetV3, ResNet-50, EfficientNet)
- **Epoch slider** (10–200)
- Animated training progress bar with live loss/accuracy
- Image upload simulation for dataset expansion

### Module 6 — Reports & Analytics
- Full statistics computed by Java backend
- **Environmental impact** — CO₂ saved, trees equivalent, diversion rate
- **Bar chart** — waste by category (kg)
- **Line chart** — monthly recycling trend
- **Zone summary** — avg fill, critical count per zone
- **Alert management** — dismiss individual or all alerts
- Export PDF option

---

## 6. JAVA REST API ENDPOINTS

| Method | Endpoint                    | Description                        |
|--------|-----------------------------|------------------------------------|
| POST   | `/api/login`                | Authenticate — checks MySQL        |
| POST   | `/api/signup`               | Register — inserts into MySQL      |
| POST   | `/api/logout`               | Invalidate session token           |
| GET    | `/api/bins`                 | All bins with live sensor data     |
| POST   | `/api/bins/{id}/pickup`     | Schedule pickup, reset fill level  |
| POST   | `/api/bins/{id}/maintain`   | Log maintenance, update date       |
| GET    | `/api/alerts`               | All active system alerts           |
| POST   | `/api/alerts/{id}/dismiss`  | Dismiss a specific alert           |
| GET    | `/api/stats`                | Dashboard statistics               |
| GET    | `/api/detections`           | Recent AI detection results        |
| POST   | `/api/detections`           | Classify a waste item (hint param) |
| GET    | `/api/zones`                | Zone-wise bin summary              |
| GET    | `/api/reports`              | Full report data                   |

---

## 7. JAVA BUSINESS LOGIC (Key Classes & Methods)

| Method / Class         | What it does                                             |
|------------------------|----------------------------------------------------------|
| `classifyWaste(hint)`  | AI keyword engine — maps 100+ waste items to categories  |
| `runAlertEngine()`     | Scans bins, auto-creates alerts at 90%+ fill             |
| `startSensorThread()`  | Background thread — updates bin data every 10s           |
| `login(u, p)`          | Queries MySQL, creates UUID session token                |
| `signup(u, p, r, e)`   | Inserts new user into MySQL `auth_system.users`          |
| `buildReportJson()`    | Computes CO₂ saved, recycling rate, zone stats           |
| `buildZoneSummaryJson()` | Zone-by-zone bin analysis                              |
| `Bin.fillColor()`      | Returns hex color based on fill threshold               |
| `Bin.urgency()`        | Returns CRITICAL / WARNING / NORMAL                     |
| `Bin.hoursUntilFull()` | Estimates hours remaining before bin overflows          |

---

## 8. DATABASE SCHEMA

```sql
-- Database: auth_system
CREATE TABLE users (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(100) NOT NULL UNIQUE,
  email       VARCHAR(100) NOT NULL UNIQUE,
  password    VARCHAR(255) NOT NULL,
  role        VARCHAR(100) DEFAULT 'Waste Operator',
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  last_login  TIMESTAMP NULL
);
```

---

## 9. HOW TO RUN

### Prerequisites
- Java 21+
- MySQL 8.0 running with `auth_system` database
- Node.js (for npm commands only)
- Any modern browser

### Steps
```bash
# 1. Navigate to project folder
cd "smart segregation system"

# 2. Compile (first time or after Java changes)
npm run build

# 3. Start the server
npm start

# 4. Open browser
http://localhost:3000/login.html
```

### Demo Credentials
| Username   | Password      | Role                  |
|------------|---------------|-----------------------|
| admin      | segregate2025 | System Administrator  |
| operator   | greenbin      | Waste Operator        |
| supervisor | eco123        | Site Supervisor       |
| tech       | smartbin      | IoT Technician        |

---

## 10. PPT SLIDE CONTENT

### Slide 1 — Title
- **Smart Segregation System**
- AI-Powered Waste Management Solution
- Team: G Dineep Chandra

### Slide 2 — Problem Statement
- Manual waste management is inefficient
- Bins overflow before collection
- Recyclables mixed with hazardous waste
- No real-time visibility or analytics
- High cost, low efficiency

### Slide 3 — Proposed Solution
- AI classification of waste items using image upload
- IoT-simulated smart bins with live sensor data
- Automated alerts for critical fill levels
- Role-based login system connected to MySQL
- Full analytics dashboard with charts and reports

### Slide 4 — Tech Stack
- **Backend:** Java 21 (no framework, plain JDK)
- **Frontend:** HTML5 + CSS3 + JavaScript (SPA)
- **Database:** MySQL 8.0 (auth_system)
- **AI/ML:** TensorFlow.js COCO-SSD + Java engine
- **Charts:** Chart.js | **Icons:** Font Awesome

### Slide 5 — System Architecture
- 3-tier: Browser → Java Server → MySQL
- REST API (13 endpoints)
- Java background thread for sensor simulation
- Session-based auth with UUID tokens

### Slide 6 — Key Features
1. MySQL-connected Login + Sign Up
2. Real-time dashboard (auto-refresh 8s)
3. Image upload waste detection
4. 10 smart bins across 5 zones
5. AI alert engine (auto-generates at 90%+)
6. Reports with CO₂ impact metrics

### Slide 7 — AI Classification Engine
- Java `classifyWaste()` method
- 100+ keyword mappings
- Categories: Organic, Recyclable, Hazardous, E-Waste, Residual
- TF.js COCO-SSD for image-based detection
- Confidence scoring (75–99%)

### Slide 8 — Database Design
- Single table: `auth_system.users`
- Stores: username, email, password, role, last_login
- Login updates `last_login` timestamp
- Sign Up inserts new row permanently

### Slide 9 — Results / Output
- Login system working with MySQL
- Real-time bin sensor updates every 10s
- Image detection with bounding boxes
- Alert auto-generation working
- Reports showing recycling + CO₂ data

### Slide 10 — Conclusion & Future Work
- Current: Simulated IoT + rule-based AI
- Future: Real hardware sensors (MQTT/WebSocket)
- Future: Actual ML model (YOLO / MobileNet trained on waste)
- Future: Mobile app integration
- Future: Cloud deployment (AWS/GCP)

---

## 11. PROJECT FILE STRUCTURE

```
smart segregation system/
│
├── SmartSegServer.java        ← Java backend (all business logic)
├── SmartSegServer.class       ← compiled bytecode
├── mysql-connector-j-8.0.33.jar  ← MySQL JDBC driver
├── package.json               ← npm scripts (npm start / npm run build)
│
├── index.html                 ← Main SPA (Dashboard, all 5 pages)
├── login.html                 ← Login page → redirects to index.html
├── signup.html                ← Sign up page → saves to MySQL
│
├── (legacy pages)
│   ├── app.html
│   ├── dashboard.html
│   ├── waste-monitoring.html
│   ├── bin-management.html
│   ├── ai-training.html
│   ├── reports.html
│   └── alerts.html
│
└── PROJECT_DOCUMENTATION.md   ← This file
```

---

*Smart Segregation System — Zero Waste Initiative · v2.0*
