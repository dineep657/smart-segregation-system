import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.time.format.*;

/**
 * =====================================================================
 *  Smart Segregation System — Java Backend
 *  All main business logic lives here in Java.
 *  HTML/CSS/JS only handles UI and calls these REST endpoints.
 *
 *  ENDPOINTS:
 *   POST /api/login              — authenticate user
 *   POST /api/logout             — logout
 *   POST /api/signup             — register new user
 *   GET  /api/bins               — get all bins (live sensor data)
 *   GET  /api/bins/{id}          — get single bin
 *   POST /api/bins/{id}/pickup   — schedule pickup
 *   POST /api/bins/{id}/maintain — log maintenance
 *   GET  /api/alerts             — get all alerts
 *   POST /api/alerts/{id}/dismiss — dismiss alert
 *   GET  /api/stats              — dashboard statistics
 *   GET  /api/detections         — AI detection results
 *   POST /api/detect             — classify waste item (AI engine)
 *   GET  /api/reports            — full report data
 *   GET  /api/zones              — zone summary
 * =====================================================================
 */
public class SmartSegServer {

    static final int PORT = 3000;
    static final String ROOT = System.getProperty("user.dir");
    static final Random RND  = new Random();

    // ===================== DATA MODELS =====================

    /** Represents a smart waste bin with IoT sensor data */
    static class Bin {
        String id, name, type, zone, location, status, lastMaintenance;
        int fill, temperature, weight, capacity;
        boolean alertSent;

        Bin(String id,String name,String type,String zone,
            String location,int fill,int temp,int weight,String status){
            this.id=id; this.name=name; this.type=type; this.zone=zone;
            this.location=location; this.fill=fill; this.temperature=temp;
            this.weight=weight; this.status=status; this.capacity=500;
            this.lastMaintenance="2024-01-15"; this.alertSent=false;
        }

        /** Java business logic: compute fill colour based on threshold */
        String fillColor(){
            if(fill>=90) return "#e74c3c";
            if(fill>=75) return "#f39c12";
            return "#2ecc71";
        }

        /** Java business logic: determine urgency level */
        String urgency(){
            if(fill>=90) return "CRITICAL";
            if(fill>=75) return "WARNING";
            return "NORMAL";
        }

        /** Java business logic: compute estimated hours until full */
        double hoursUntilFull(){
            if(fill>=100) return 0;
            double fillRate = 2.5; // avg % per hour
            return (100.0-fill)/fillRate;
        }

        String toJson(){
            return String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"type\":\"%s\",\"zone\":\"%s\"," +
                "\"location\":\"%s\",\"fill\":%d,\"temperature\":%d,\"weight\":%d," +
                "\"capacity\":%d,\"status\":\"%s\",\"lastMaintenance\":\"%s\"," +
                "\"fillColor\":\"%s\",\"urgency\":\"%s\",\"hoursUntilFull\":%.1f}",
                id,name,type,zone,location,fill,temperature,weight,
                capacity,status,lastMaintenance,fillColor(),urgency(),hoursUntilFull());
        }
    }

    /** Represents a system alert */
    static class Alert {
        static int counter = 1;
        int id; String title, time, type, icon, source; boolean dismissed;

        Alert(String title,String time,String type,String icon,String source){
            this.id=counter++; this.title=title; this.time=time;
            this.type=type; this.icon=icon; this.source=source; this.dismissed=false;
        }

        String toJson(){
            return String.format(
                "{\"id\":%d,\"title\":\"%s\",\"time\":\"%s\",\"type\":\"%s\"," +
                "\"icon\":\"%s\",\"source\":\"%s\",\"dismissed\":%b}",
                id,title,time,type,icon,source,dismissed);
        }
    }

    /** Represents an AI waste detection result */
    static class Detection {
        String item, category, bin, icon, color, timestamp; int confidence;

        Detection(String item,String category,String bin,
                  String icon,String color,int confidence){
            this.item=item; this.category=category; this.bin=bin;
            this.icon=icon; this.color=color; this.confidence=confidence;
            this.timestamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        String toJson(){
            return String.format(
                "{\"item\":\"%s\",\"category\":\"%s\",\"bin\":\"%s\"," +
                "\"icon\":\"%s\",\"color\":\"%s\",\"confidence\":%d,\"timestamp\":\"%s\"}",
                item,category,bin,icon,color,confidence,timestamp);
        }
    }

    /** Represents a registered user */
    static class User {
        String username, password, role, email; LocalDateTime lastLogin;
        User(String u,String p,String r,String e){
            username=u; password=p; role=r; email=e;
        }
    }

    // ===================== IN-MEMORY DATABASE =====================

    static List<Bin>       binDB       = new ArrayList<>();
    static List<Alert>     alertDB     = new ArrayList<>();
    static List<Detection> detectionDB = new ArrayList<>();
    static List<User>      userDB      = new ArrayList<>();
    static Map<String,String> sessions = new ConcurrentHashMap<>(); // token->username
    static int pickupsToday = 12;
    static int totalWasteKg = 12876;

    // ===================== AI CLASSIFICATION ENGINE =====================
    // All AI detection logic runs in Java

    static final String[][] WASTE_ITEMS = {
        // Recyclable
        {"Plastic Bottle",     "Recyclable",  "Recyclables Bin (Blue)",   "fas fa-recycle",          "#3498db", "88,97"},
        {"Glass Bottle",       "Recyclable",  "Recyclables Bin (Blue)",   "fas fa-wine-bottle",      "#3498db", "90,98"},
        {"Aluminium Can",      "Recyclable",  "Recyclables Bin (Blue)",   "fas fa-beer",             "#3498db", "87,96"},
        {"Cardboard Box",      "Recyclable",  "Recyclables Bin (Blue)",   "fas fa-box",              "#3498db", "89,96"},
        {"Newspaper",          "Recyclable",  "Recyclables Bin (Blue)",   "fas fa-newspaper",        "#3498db", "91,98"},
        {"Metal Can",          "Recyclable",  "Recyclables Bin (Blue)",   "fas fa-trash-alt",        "#3498db", "86,95"},
        {"Plastic Bag",        "Recyclable",  "Recyclables Bin (Blue)",   "fas fa-shopping-bag",     "#3498db", "82,93"},
        {"Tin Container",      "Recyclable",  "Recyclables Bin (Blue)",   "fas fa-archive",          "#3498db", "85,94"},
        // Organic
        {"Apple Core",         "Organic",     "Organic Bin (Green)",      "fas fa-apple-alt",        "#2ecc71", "91,99"},
        {"Banana Peel",        "Organic",     "Organic Bin (Green)",      "fas fa-leaf",             "#2ecc71", "93,99"},
        {"Food Scraps",        "Organic",     "Organic Bin (Green)",      "fas fa-seedling",         "#2ecc71", "88,96"},
        {"Plant Waste",        "Organic",     "Organic Bin (Green)",      "fas fa-pagelines",        "#2ecc71", "85,95"},
        {"Vegetable Scraps",   "Organic",     "Organic Bin (Green)",      "fas fa-carrot",           "#2ecc71", "89,97"},
        {"Coffee Grounds",     "Organic",     "Organic Bin (Green)",      "fas fa-coffee",           "#2ecc71", "87,96"},
        {"Grass Clippings",    "Organic",     "Organic Bin (Green)",      "fas fa-leaf",             "#2ecc71", "86,95"},
        {"Fruit Peels",        "Organic",     "Organic Bin (Green)",      "fas fa-lemon",            "#2ecc71", "90,98"},
        {"Egg Shells",         "Organic",     "Organic Bin (Green)",      "fas fa-egg",              "#2ecc71", "84,94"},
        {"Tea Bags",           "Organic",     "Organic Bin (Green)",      "fas fa-mug-hot",          "#2ecc71", "83,93"},
        // Hazardous
        {"AA Battery",         "Hazardous",   "Hazardous Bin (Red)",      "fas fa-battery-full",     "#e67e22", "85,95"},
        {"Medical Syringe",    "Hazardous",   "Hazardous Bin (Red)",      "fas fa-syringe",          "#e67e22", "86,97"},
        {"Paint Can",          "Hazardous",   "Hazardous Bin (Red)",      "fas fa-paint-roller",     "#e67e22", "83,94"},
        {"Chemical Bottle",    "Hazardous",   "Hazardous Bin (Red)",      "fas fa-flask",            "#e67e22", "88,96"},
        {"Fluorescent Bulb",   "Hazardous",   "Hazardous Bin (Red)",      "fas fa-lightbulb",        "#e67e22", "84,93"},
        {"Motor Oil",          "Hazardous",   "Hazardous Bin (Red)",      "fas fa-oil-can",          "#e67e22", "87,95"},
        {"Pesticide Can",      "Hazardous",   "Hazardous Bin (Red)",      "fas fa-skull-crossbones", "#e67e22", "89,97"},
        // E-Waste
        {"Old Mobile Phone",   "E-Waste",     "E-Waste Bin (Yellow)",     "fas fa-mobile-alt",       "#f1c40f", "82,94"},
        {"Broken Circuit",     "E-Waste",     "E-Waste Bin (Yellow)",     "fas fa-microchip",        "#f1c40f", "80,92"},
        {"Laptop",             "E-Waste",     "E-Waste Bin (Yellow)",     "fas fa-laptop",           "#f1c40f", "84,95"},
        {"Remote Control",     "E-Waste",     "E-Waste Bin (Yellow)",     "fas fa-tv",               "#f1c40f", "81,92"},
        {"Headphones",         "E-Waste",     "E-Waste Bin (Yellow)",     "fas fa-headphones",       "#f1c40f", "83,93"},
        {"Keyboard",           "E-Waste",     "E-Waste Bin (Yellow)",     "fas fa-keyboard",         "#f1c40f", "82,93"},
        {"Power Cable",        "E-Waste",     "E-Waste Bin (Yellow)",     "fas fa-plug",             "#f1c40f", "79,91"},
        {"Computer Mouse",     "E-Waste",     "E-Waste Bin (Yellow)",     "fas fa-mouse",            "#f1c40f", "81,92"},
        // Residual
        {"Food Wrapper",       "Residual",    "Residual Bin (Grey)",      "fas fa-trash",            "#95a5a6", "75,90"},
        {"Polystyrene Cup",    "Residual",    "Residual Bin (Grey)",      "fas fa-coffee",           "#95a5a6", "78,91"},
        {"Used Tissue",        "Residual",    "Residual Bin (Grey)",      "fas fa-scroll",           "#95a5a6", "76,89"},
        {"Ceramic Piece",      "Residual",    "Residual Bin (Grey)",      "fas fa-cookie",           "#95a5a6", "77,90"},
        {"Rubber Item",        "Residual",    "Residual Bin (Grey)",      "fas fa-circle",           "#95a5a6", "74,88"},
        {"Broken Toy",         "Residual",    "Residual Bin (Grey)",      "fas fa-shapes",           "#95a5a6", "75,89"},
    };

    /**
     * Java AI Classification Engine — Expanded Keyword Matcher
     * Covers 100+ real-world waste item keywords mapped to correct categories.
     */
    static Detection classifyWaste(String hint) {
        String[] item;
        if (hint != null && !hint.isEmpty()) {
            hint = hint.toLowerCase().trim();

            // ---- ORGANIC ----
            if      (hint.contains("plant") || hint.contains("potted") || hint.contains("flower") ||
                     hint.contains("shrub") || hint.contains("bush") || hint.contains("weed"))
                                                                              item = WASTE_ITEMS[11]; // Plant Waste
            else if (hint.contains("grass") || hint.contains("lawn") || hint.contains("clip"))
                                                                              item = WASTE_ITEMS[14]; // Grass
            else if (hint.contains("veg") || hint.contains("broccoli") || hint.contains("carrot") ||
                     hint.contains("tomato") || hint.contains("potato") || hint.contains("onion"))
                                                                              item = WASTE_ITEMS[12]; // Vegetable
            else if (hint.contains("fruit") || hint.contains("orange") || hint.contains("mango") ||
                     hint.contains("peel") || hint.contains("skin") || hint.contains("lemon"))
                                                                              item = WASTE_ITEMS[15]; // Fruit Peels
            else if (hint.contains("apple"))                                  item = WASTE_ITEMS[8];
            else if (hint.contains("banana"))                                 item = WASTE_ITEMS[9];
            else if (hint.contains("food") || hint.contains("scrap") || hint.contains("leftover") ||
                     hint.contains("meal") || hint.contains("rice") || hint.contains("bread") ||
                     hint.contains("pizza") || hint.contains("sandwich") || hint.contains("cake"))
                                                                              item = WASTE_ITEMS[10]; // Food Scraps
            else if (hint.contains("coffee") || hint.contains("ground") || hint.contains("tea") ||
                     hint.contains("bag"))                                    item = WASTE_ITEMS[13]; // Coffee/Tea
            else if (hint.contains("egg") || hint.contains("shell"))         item = WASTE_ITEMS[16]; // Egg shells
            else if (hint.contains("leaf") || hint.contains("leaves") || hint.contains("branch") ||
                     hint.contains("twig") || hint.contains("wood") || hint.contains("bark"))
                                                                              item = WASTE_ITEMS[11]; // Plant Waste

            // ---- RECYCLABLE ----
            else if (hint.contains("plastic") || hint.contains("bottle") || hint.contains("pet"))
                                                                              item = WASTE_ITEMS[0];
            else if (hint.contains("glass") || hint.contains("jar") || hint.contains("vase"))
                                                                              item = WASTE_ITEMS[1];
            else if (hint.contains("alum") || hint.contains("soda") || hint.contains("beer") ||
                     hint.contains("tin") || hint.contains("steel can"))     item = WASTE_ITEMS[2];
            else if (hint.contains("card") || hint.contains("box") || hint.contains("carton"))
                                                                              item = WASTE_ITEMS[3];
            else if (hint.contains("paper") || hint.contains("news") || hint.contains("magazine") ||
                     hint.contains("book") || hint.contains("notebook"))     item = WASTE_ITEMS[4];
            else if (hint.contains("metal") || hint.contains("iron") || hint.contains("steel") ||
                     hint.contains("copper") || hint.contains("scrap metal"))item = WASTE_ITEMS[5];
            else if (hint.contains("bag") || hint.contains("polythene") || hint.contains("wrapper poly"))
                                                                              item = WASTE_ITEMS[6];

            // ---- HAZARDOUS ----
            else if (hint.contains("battery") || hint.contains("cell battery"))
                                                                              item = WASTE_ITEMS[18];
            else if (hint.contains("syringe") || hint.contains("needle") || hint.contains("medical") ||
                     hint.contains("medicine") || hint.contains("pill") || hint.contains("tablet"))
                                                                              item = WASTE_ITEMS[19];
            else if (hint.contains("paint") || hint.contains("spray") || hint.contains("aerosol"))
                                                                              item = WASTE_ITEMS[20];
            else if (hint.contains("chemical") || hint.contains("acid") || hint.contains("bleach") ||
                     hint.contains("solvent") || hint.contains("thinner") || hint.contains("poison"))
                                                                              item = WASTE_ITEMS[21];
            else if (hint.contains("bulb") || hint.contains("tube light") || hint.contains("fluorescent") ||
                     hint.contains("mercury") || hint.contains("cfl"))       item = WASTE_ITEMS[22];
            else if (hint.contains("oil") || hint.contains("grease") || hint.contains("fuel") ||
                     hint.contains("petrol") || hint.contains("diesel"))     item = WASTE_ITEMS[23];
            else if (hint.contains("pest") || hint.contains("insect") || hint.contains("herbicide") ||
                     hint.contains("fertilizer") || hint.contains("weed killer"))
                                                                              item = WASTE_ITEMS[24];
            else if (hint.contains("hazard") || hint.contains("toxic") || hint.contains("danger"))
                                                                              item = WASTE_ITEMS[21];

            // ---- E-WASTE ----
            else if (hint.contains("cable") || hint.contains("wire") || hint.contains("charger") ||
                     hint.contains("cord") || hint.contains("plug") || hint.contains("adapter") ||
                     hint.contains("socket") || hint.contains("connector"))  item = WASTE_ITEMS[31]; // Power Cable
            else if (hint.contains("phone") || hint.contains("mobile") || hint.contains("smartphone") ||
                     hint.contains("iphone") || hint.contains("android"))    item = WASTE_ITEMS[25]; // Mobile Phone
            else if (hint.contains("circuit") || hint.contains("pcb") || hint.contains("board") ||
                     hint.contains("chip") || hint.contains("semiconductor")) item = WASTE_ITEMS[26]; // Broken Circuit
            else if (hint.contains("laptop") || hint.contains("computer") || hint.contains("tablet") ||
                     hint.contains("desktop") || hint.contains("monitor") || hint.contains("screen"))
                                                                              item = WASTE_ITEMS[27]; // Laptop
            else if (hint.contains("remote") || hint.contains("tv") || hint.contains("television"))
                                                                              item = WASTE_ITEMS[28]; // Remote
            else if (hint.contains("headphone") || hint.contains("earphone") || hint.contains("speaker") ||
                     hint.contains("audio"))                                  item = WASTE_ITEMS[29]; // Headphones
            else if (hint.contains("keyboard"))                               item = WASTE_ITEMS[30]; // Keyboard
            else if (hint.contains("mouse") || hint.contains("printer") || hint.contains("scanner"))
                                                                              item = WASTE_ITEMS[32]; // Computer Mouse
            else if (hint.contains("elec") || hint.contains("gadget") || hint.contains("device") ||
                     hint.contains("tech") || hint.contains("ewaste") || hint.contains("e-waste"))
                                                                              item = WASTE_ITEMS[26]; // Broken Circuit

            // ---- RESIDUAL ----
            else if (hint.contains("tissue") || hint.contains("napkin") || hint.contains("toilet"))
                                                                              item = WASTE_ITEMS[35]; // Used Tissue
            else if (hint.contains("styrofoam") || hint.contains("polystyrene") || hint.contains("foam"))
                                                                              item = WASTE_ITEMS[34]; // Polystyrene
            else if (hint.contains("ceramic") || hint.contains("china") || hint.contains("clay") ||
                     hint.contains("pottery"))                                item = WASTE_ITEMS[36]; // Ceramic
            else if (hint.contains("rubber") || hint.contains("tyre") || hint.contains("tire"))
                                                                              item = WASTE_ITEMS[37]; // Rubber
            else if (hint.contains("toy") || hint.contains("doll") || hint.contains("game"))
                                                                              item = WASTE_ITEMS[38]; // Broken Toy
            else if (hint.contains("diaper") || hint.contains("sanitary") || hint.contains("pad"))
                                                                              item = WASTE_ITEMS[33]; // Food Wrapper/Residual
            else                                                              item = WASTE_ITEMS[33]; // default residual

        } else {
            item = WASTE_ITEMS[RND.nextInt(WASTE_ITEMS.length)];
        }
        String[] range = item[5].split(",");
        int lo = Integer.parseInt(range[0]), hi = Integer.parseInt(range[1]);
        int confidence = lo + RND.nextInt(hi - lo + 1);
        Detection d = new Detection(item[0],item[1],item[2],item[3],item[4],confidence);
        updateBinOnDetection(item[1]);
        detectionDB.add(0, d);
        if (detectionDB.size() > 30) detectionDB.remove(detectionDB.size()-1);
        totalWasteKg += RND.nextInt(3) + 1;
        return d;
    }

    /** Java business logic: when AI detects waste, increase the correct bin fill */
    static void updateBinOnDetection(String category) {
        Map<String,String> catToBinId = Map.of(
            "Organic","B-101","Recyclable","B-102","Hazardous","B-103",
            "Residual","B-104","E-Waste","B-105"
        );
        String binId = catToBinId.get(category);
        if (binId != null) {
            binDB.stream().filter(b->b.id.equals(binId)).findFirst().ifPresent(b->{
                if ("online".equals(b.status)) b.fill = Math.min(100, b.fill + 1);
            });
        }
    }

    // ===================== ALERT ENGINE =====================
    // Java checks bin levels and auto-generates alerts

    /** Java business logic: scan all bins and generate alerts for critical ones */
    static void runAlertEngine() {
        for (Bin b : binDB) {
            if (!"online".equals(b.status)) continue;
            boolean alreadyAlerted = alertDB.stream()
                .anyMatch(a -> !a.dismissed && a.source.equals(b.id) && a.type.equals("warning"));
            if (b.fill >= 90 && !alreadyAlerted) {
                Alert a = new Alert(
                    b.name + " (" + b.id + ") at " + b.fill + "% — Schedule pickup immediately!",
                    "Just now", "warning", "fas fa-exclamation-triangle", b.id
                );
                alertDB.add(0, a);
                if (alertDB.size() > 50) alertDB.remove(alertDB.size()-1);
            }
            if (b.temperature > 35) {
                boolean tempAlert = alertDB.stream()
                    .anyMatch(a->!a.dismissed && a.source.equals(b.id) && a.title.contains("Temperature"));
                if (!tempAlert) {
                    alertDB.add(0, new Alert(
                        "⚠ Temperature alert: " + b.name + " at " + b.temperature + "°C",
                        "Just now","danger","fas fa-thermometer-full", b.id
                    ));
                }
            }
        }
    }

    // ===================== SENSOR SIMULATION (Java Thread) =====================

    /** Java background thread: simulates live IoT sensor updates every 10 seconds */
    static void startSensorThread() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            for (Bin b : binDB) {
                if (!"online".equals(b.status)) {
                    // small chance of coming back online
                    if (RND.nextDouble() > 0.97) { b.status = "online"; addAlert("Bin "+b.id+" ("+b.name+") is back online","1 min ago","info","fas fa-check-circle",b.id); }
                    continue;
                }
                b.fill        = clamp(b.fill + RND.nextInt(5) - 2, 0, 100);
                b.temperature = clamp(b.temperature + RND.nextInt(3) - 1, 15, 45);
                b.weight      = Math.max(0, b.weight + RND.nextInt(6) - 3);
                // small chance of going offline
                if (RND.nextDouble() > 0.98) { b.status = "offline"; addAlert("Sensor offline: "+b.name+" ("+b.id+")","Just now","danger","fas fa-wifi",b.id); }
            }
            // Run alert engine on every sensor tick
            runAlertEngine();
            // Simulate a new auto-detection every 10s
            classifyWaste(null);
        }, 0, 10, TimeUnit.SECONDS);
    }

    static int clamp(int v, int lo, int hi){ return Math.max(lo, Math.min(hi, v)); }

    static void addAlert(String title,String time,String type,String icon,String src){
        alertDB.add(0, new Alert(title,time,type,icon,src));
        if(alertDB.size()>50) alertDB.remove(alertDB.size()-1);
    }

    // ===================== MYSQL DATABASE CONFIG =====================
    static final String DB_URL  = "jdbc:mysql://localhost:3306/auth_system?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    static final String DB_USER = "root";
    static final String DB_PASS = "Dineep123";

    // Test MySQL connection on startup
    static boolean mysqlAvailable = false;

    static void initMySQL() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            java.sql.Connection conn = java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            conn.close();
            mysqlAvailable = true;
            System.out.println("  ✅  MySQL connected → auth_system database");
        } catch (Exception e) {
            mysqlAvailable = false;
            System.out.println("  ⚠   MySQL not available — using in-memory users. Error: " + e.getMessage());
        }
    }

    /** Java: validate login against MySQL first, fallback to in-memory */
    static String login(String username, String password) {
        if (mysqlAvailable) {
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                java.sql.PreparedStatement ps = conn.prepareStatement(
                    "SELECT username, email, role FROM users WHERE username = ? AND password = ?");
                ps.setString(1, username);
                ps.setString(2, password);
                java.sql.ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String role  = rs.getString("role");
                    String email = rs.getString("email");
                    // Update last_login
                    java.sql.PreparedStatement upd = conn.prepareStatement(
                        "UPDATE users SET last_login = NOW() WHERE username = ?");
                    upd.setString(1, username);
                    upd.executeUpdate();
                    String token = UUID.randomUUID().toString();
                    sessions.put(token, username);
                    // Sync to in-memory if not present
                    if (userDB.stream().noneMatch(u -> u.username.equals(username))) {
                        userDB.add(new User(username, password, role != null ? role : "Waste Operator", email != null ? email : ""));
                    }
                    addAlert("User '" + username + "' logged in via MySQL", "Just now", "info", "fas fa-sign-in-alt", "auth");
                    System.out.println("  ✅  MySQL login: " + username + " [" + role + "]");
                    return token;
                }
                System.out.println("  ❌  MySQL: invalid credentials for: " + username);
                return null;
            } catch (Exception e) {
                System.out.println("  ⚠   MySQL error: " + e.getMessage() + " — using in-memory fallback");
            }
        }
        // Fallback: in-memory
        return userDB.stream()
            .filter(u -> u.username.equals(username) && u.password.equals(password))
            .findFirst()
            .map(u -> {
                u.lastLogin = LocalDateTime.now();
                String token = UUID.randomUUID().toString();
                sessions.put(token, username);
                addAlert("User '" + username + "' logged in (offline mode)", "Just now", "info", "fas fa-sign-in-alt", "auth");
                return token;
            }).orElse(null);
    }

    /** Java: register new user — saves to MySQL + in-memory */
    static boolean signup(String username, String password, String role, String email) {
        boolean exists = userDB.stream().anyMatch(u -> u.username.equals(username));
        if (exists) return false;

        // Save to MySQL
        if (mysqlAvailable) {
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, email, password, role) VALUES (?, ?, ?, ?)");
                ps.setString(1, username);
                ps.setString(2, email != null && !email.isEmpty() ? email : username + "@smartseg.io");
                ps.setString(3, password);
                ps.setString(4, role != null && !role.isEmpty() ? role : "Waste Operator");
                ps.executeUpdate();
                System.out.println("  ✅  MySQL signup: " + username + " saved to auth_system.users");
            } catch (Exception e) {
                System.out.println("  ⚠   MySQL signup error: " + e.getMessage());
                if (e.getMessage() != null && e.getMessage().contains("Duplicate")) return false;
            }
        }
        userDB.add(new User(username, password, role != null ? role : "Waste Operator", email != null ? email : ""));
        return true;
    }

    /** Java: validate session token */
    static User getSessionUser(String token) {
        String username = sessions.get(token);
        if (username == null) return null;
        return userDB.stream().filter(u -> u.username.equals(username)).findFirst().orElse(null);
    }

    // ===================== REPORTS BUSINESS LOGIC =====================

    /** Java: compute full report statistics */
    static String buildReportJson() {
        long critical  = binDB.stream().filter(b -> b.fill >= 90).count();
        long warning   = binDB.stream().filter(b -> b.fill >= 75 && b.fill < 90).count();
        long online    = binDB.stream().filter(b -> "online".equals(b.status)).count();
        long offline   = binDB.stream().filter(b -> "offline".equals(b.status)).count();
        double avgFill = binDB.stream().mapToInt(b->b.fill).average().orElse(0);
        int  totalAlerts  = (int) alertDB.stream().filter(a->!a.dismissed).count();
        int  warnAlerts   = (int) alertDB.stream().filter(a->!a.dismissed && "warning".equals(a.type)).count();
        int  dangerAlerts = (int) alertDB.stream().filter(a->!a.dismissed && "danger".equals(a.type)).count();
        // waste by category (computed in Java)
        Map<String,Integer> byCategory = new LinkedHashMap<>();
        byCategory.put("Organic",     detectionDB.stream().filter(d->"Organic".equals(d.category)).mapToInt(d->1).sum() * 8 + 400);
        byCategory.put("Recyclable",  detectionDB.stream().filter(d->"Recyclable".equals(d.category)).mapToInt(d->1).sum() * 6 + 300);
        byCategory.put("Hazardous",   detectionDB.stream().filter(d->"Hazardous".equals(d.category)).mapToInt(d->1).sum() * 3 + 80);
        byCategory.put("Residual",    detectionDB.stream().filter(d->"Residual".equals(d.category)).mapToInt(d->1).sum() * 5 + 200);
        byCategory.put("E-Waste",     detectionDB.stream().filter(d->"E-Waste".equals(d.category)).mapToInt(d->1).sum() * 4 + 100);
        double recyclingRate  = 68.4 + (RND.nextDouble() * 2 - 1);
        double diversionRate  = 72.3 + (RND.nextDouble() * 2 - 1);
        double co2Saved       = totalWasteKg * 0.00025;
        int    treesEquiv     = (int)(co2Saved * 43.7);
        return String.format(
            "{\"totalWaste\":\"%,d kg\",\"recyclingRate\":\"%.1f%%\",\"diversionRate\":\"%.1f%%\"," +
            "\"co2Saved\":\"%.2f tons\",\"treesEquiv\":%d,\"pickupsToday\":%d," +
            "\"aiAccuracy\":\"94.7%%\",\"activeBins\":%d,\"offlineBins\":%d,\"totalBins\":%d," +
            "\"criticalBins\":%d,\"warningBins\":%d,\"avgFillLevel\":\"%.1f%%\"," +
            "\"totalAlerts\":%d,\"warningAlerts\":%d,\"dangerAlerts\":%d,\"totalDetections\":%d," +
            "\"organicKg\":%d,\"recyclableKg\":%d,\"hazardousKg\":%d,\"residualKg\":%d,\"ewasteKg\":%d}",
            totalWasteKg, recyclingRate, diversionRate, co2Saved, treesEquiv, pickupsToday,
            online, offline, binDB.size(), critical, warning, avgFill,
            totalAlerts, warnAlerts, dangerAlerts, detectionDB.size(),
            byCategory.get("Organic"), byCategory.get("Recyclable"), byCategory.get("Hazardous"),
            byCategory.get("Residual"), byCategory.get("E-Waste"));
    }

    /** Java: compute zone summary */
    static String buildZoneSummaryJson() {
        String[] zones = {"Zone A","Zone B","Zone C","Zone D","Zone E"};
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < zones.length; i++) {
            final String z = zones[i];
            List<Bin> zb = binDB.stream().filter(b->b.zone.equals(z)).collect(java.util.stream.Collectors.toList());
            double avg  = zb.stream().mapToInt(b->b.fill).average().orElse(0);
            long crit   = zb.stream().filter(b->b.fill>=90).count();
            long onl    = zb.stream().filter(b->"online".equals(b.status)).count();
            sb.append(String.format("{\"zone\":\"%s\",\"bins\":%d,\"avgFill\":%.1f,\"critical\":%d,\"online\":%d}",
                z, zb.size(), avg, crit, onl));
            if (i < zones.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    // ===================== DATA INITIALISATION =====================

    static void initData() {
        // Users
        userDB.add(new User("admin",      "segregate2025", "System Administrator", "admin@smartseg.io"));
        userDB.add(new User("operator",   "greenbin",      "Waste Operator",       "operator@smartseg.io"));
        userDB.add(new User("supervisor", "eco123",        "Site Supervisor",      "supervisor@smartseg.io"));
        userDB.add(new User("tech",       "smartbin",      "IoT Technician",       "tech@smartseg.io"));

        // Bins (10 bins across 5 zones)
        binDB.add(new Bin("B-101","Organic Waste",     "organic",    "Zone A","Kitchen Area",     87,24,342,"online"));
        binDB.add(new Bin("B-102","Recyclables",       "recyclable", "Zone A","Cafeteria",        94,22,512,"online"));
        binDB.add(new Bin("B-103","Hazardous Waste",   "hazardous",  "Zone B","Lab Area",         42,26,78, "online"));
        binDB.add(new Bin("B-104","Residual Waste",    "residual",   "Zone B","Loading Dock",     65,23,289,"online"));
        binDB.add(new Bin("B-105","E-Waste",           "ewaste",     "Zone C","IT Department",    31,21,156,"online"));
        binDB.add(new Bin("B-106","Glass & Metal",     "recyclable", "Zone C","Recycling Centre", 78,24,423,"online"));
        binDB.add(new Bin("B-107","Compost",           "organic",    "Zone D","Garden Area",      92,28,567,"online"));
        binDB.add(new Bin("B-108","Medical Waste",     "hazardous",  "Zone D","Clinic",           23,19,45, "offline"));
        binDB.add(new Bin("B-109","Plastics",          "recyclable", "Zone A","Packaging Area",   56,22,298,"online"));
        binDB.add(new Bin("B-110","Construction Waste","residual",   "Zone E","Workshop",         44,20,876,"online"));

        // Initial alerts
        alertDB.add(new Alert("Recyclables bin at 92% — Schedule pickup immediately","2 hours ago","warning","fas fa-truck","B-102"));
        alertDB.add(new Alert("AI model update available (v2.4.1)","5 hours ago","info","fas fa-download","system"));
        alertDB.add(new Alert("Sensor B-108 offline — Maintenance required","Yesterday","danger","fas fa-wifi","B-108"));
        alertDB.add(new Alert("Peak waste hour detected: 2–4 PM","Yesterday","info","fas fa-chart-line","system"));
        alertDB.add(new Alert("Compost bin at 92% — Critical level reached","3 hours ago","warning","fas fa-exclamation-triangle","B-107"));

        // Seed 10 initial detections
        for (int i = 0; i < 10; i++) classifyWaste(null);
    }

    // ===================== HTTP HANDLERS =====================

    /** Base handler with CORS + JSON helpers */
    static abstract class BaseHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods","GET,POST,OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers","Content-Type,Authorization");
            if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204,-1); return; }
            try { process(ex); } catch(Exception e) { sendError(ex,500,"Internal error: "+e.getMessage()); }
        }
        abstract void process(HttpExchange ex) throws IOException;

        void sendJson(HttpExchange ex, String json) throws IOException { sendStatus(ex,200,json); }
        void sendStatus(HttpExchange ex, int code, String json) throws IOException {
            byte[] b = json.getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type","application/json;charset=utf-8");
            ex.sendResponseHeaders(code, b.length);
            ex.getResponseBody().write(b); ex.getResponseBody().close();
        }
        void sendError(HttpExchange ex, int code, String msg) throws IOException {
            sendStatus(ex, code, "{\"error\":\""+msg.replace("\"","'")+"\"}");
        }
        String readBody(HttpExchange ex) throws IOException {
            return new String(ex.getRequestBody().readAllBytes(), "UTF-8");
        }
        String extract(String json, String key) {
            String s = "\""+key+"\""; int i = json.indexOf(s); if(i<0) return "";
            i = json.indexOf(":",i)+1;
            while(i<json.length()&&json.charAt(i)==' ') i++;
            if(i>=json.length()) return "";
            if(json.charAt(i)=='"'){ i++; int e=json.indexOf('"',i); return e<0?"":json.substring(i,e); }
            int e=json.indexOf(',',i); if(e<0) e=json.indexOf('}',i); return e<0?"":json.substring(i,e).trim();
        }
        String listToJson(List<?> items) {
            StringBuilder sb = new StringBuilder("[");
            for(int i=0;i<items.size();i++){
                Object o = items.get(i);
                if(o instanceof Bin)       sb.append(((Bin)o).toJson());
                else if(o instanceof Alert) sb.append(((Alert)o).toJson());
                else if(o instanceof Detection) sb.append(((Detection)o).toJson());
                if(i<items.size()-1) sb.append(",");
            }
            return sb.append("]").toString();
        }
        String getToken(HttpExchange ex){
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if(auth!=null&&auth.startsWith("Bearer ")) return auth.substring(7);
            String q = ex.getRequestURI().getQuery();
            if(q!=null&&q.contains("token=")) {
                for(String p:q.split("&")) if(p.startsWith("token=")) return p.substring(6);
            }
            return null;
        }
        String lastSegment(HttpExchange ex){
            String p = ex.getRequestURI().getPath();
            return p.substring(p.lastIndexOf('/')+1);
        }
    }

    // --- Login ---
    static class LoginHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException {
            if(!"POST".equals(ex.getRequestMethod())){ sendError(ex,405,"POST required"); return; }
            String body = readBody(ex);
            String u = extract(body,"username"), p = extract(body,"password");
            String token = login(u,p);
            if(token!=null){
                User usr = getSessionUser(token);
                sendJson(ex,String.format("{\"success\":true,\"token\":\"%s\",\"username\":\"%s\",\"role\":\"%s\",\"email\":\"%s\"}",
                    token, usr.username, usr.role, usr.email));
            } else sendStatus(ex,401,"{\"success\":false,\"message\":\"Invalid username or password\"}");
        }
    }

    // --- Signup ---
    static class SignupHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException {
            if(!"POST".equals(ex.getRequestMethod())){ sendError(ex,405,"POST required"); return; }
            String body = readBody(ex);
            String u=extract(body,"username"),p=extract(body,"password"),
                   r=extract(body,"role"),e=extract(body,"email");
            if(u.isEmpty()||p.isEmpty()){ sendError(ex,400,"Username and password required"); return; }
            if(r.isEmpty()) r="Waste Manager";
            if(signup(u,p,r,e)){
                String token = login(u,p);
                sendJson(ex,String.format("{\"success\":true,\"token\":\"%s\",\"username\":\"%s\",\"role\":\"%s\"}",token,u,r));
            } else sendStatus(ex,409,"{\"success\":false,\"message\":\"Username already exists\"}");
        }
    }

    // --- Logout ---
    static class LogoutHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException {
            String token = getToken(ex);
            if(token!=null){ sessions.remove(token); }
            sendJson(ex,"{\"success\":true}");
        }
    }

    // --- Bins ---
    static class BinsHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            // POST /api/bins/{id}/pickup  or  /api/bins/{id}/maintain
            if("POST".equals(ex.getRequestMethod())){
                if(path.endsWith("/pickup")){
                    String id = path.split("/")[3];
                    binDB.stream().filter(b->b.id.equals(id)).findFirst().ifPresent(b->{
                        b.fill = Math.max(0, b.fill-85); pickupsToday++;
                        alertDB.removeIf(a->a.source.equals(b.id)&&"warning".equals(a.type));
                        addAlert("Pickup completed: "+b.name+" ("+b.id+") — Fill reset to "+b.fill+"%","Just now","info","fas fa-check-circle",b.id);
                    });
                    sendJson(ex,"{\"success\":true,\"message\":\"Pickup scheduled\",\"pickupsToday\":"+pickupsToday+"}");
                } else if(path.endsWith("/maintain")){
                    String id = path.split("/")[3];
                    binDB.stream().filter(b->b.id.equals(id)).findFirst().ifPresent(b->{
                        b.lastMaintenance = LocalDate.now().toString();
                        b.status = "online";
                        addAlert("Maintenance logged: "+b.name+" ("+b.id+")","Just now","info","fas fa-tools",b.id);
                    });
                    sendJson(ex,"{\"success\":true,\"message\":\"Maintenance logged\"}");
                } else sendError(ex,404,"Unknown action");
                return;
            }
            // GET /api/bins or /api/bins/{id}
            String[] parts = path.split("/");
            if(parts.length>=4 && !parts[3].isEmpty()){
                String id = parts[3];
                Bin b = binDB.stream().filter(x->x.id.equals(id)).findFirst().orElse(null);
                if(b==null) sendError(ex,404,"Bin not found");
                else sendJson(ex,b.toJson());
            } else {
                sendJson(ex,listToJson(new ArrayList<>(binDB)));
            }
        }
    }

    // --- Alerts ---
    static class AlertsHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException {
            if("POST".equals(ex.getRequestMethod())){
                String id = lastSegment(ex);
                alertDB.stream().filter(a->String.valueOf(a.id).equals(id)).findFirst()
                    .ifPresent(a->a.dismissed=true);
                sendJson(ex,"{\"success\":true}"); return;
            }
            List<Alert> active = alertDB.stream().filter(a->!a.dismissed).collect(java.util.stream.Collectors.toList());
            sendJson(ex,listToJson(new ArrayList<>(active)));
        }
    }

    // --- Stats ---
    static class StatsHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException { sendJson(ex,buildReportJson()); }
    }

    // --- Detections ---
    static class DetectionsHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException {
            if("POST".equals(ex.getRequestMethod())){
                String body = readBody(ex);
                String hint = extract(body,"hint");
                Detection d = classifyWaste(hint.isEmpty()?null:hint);
                sendJson(ex,d.toJson()); return;
            }
            // GET — return last 20 detections
            List<Detection> top = detectionDB.subList(0,Math.min(20,detectionDB.size()));
            sendJson(ex,listToJson(new ArrayList<>(top)));
        }
    }

    // --- Zones ---
    static class ZonesHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException { sendJson(ex,buildZoneSummaryJson()); }
    }

    // --- Reports ---
    static class ReportsHandler extends BaseHandler {
        void process(HttpExchange ex) throws IOException { sendJson(ex,buildReportJson()); }
    }

    // --- Static Files ---
    static class StaticHandler extends BaseHandler {
        static final Map<String,String> MIME = Map.of(
            ".html","text/html;charset=utf-8",".css","text/css",
            ".js","application/javascript",".json","application/json",
            ".png","image/png",".jpg","image/jpeg",
            ".svg","image/svg+xml",".ico","image/x-icon");
        void process(HttpExchange ex) throws IOException {
            String uri = ex.getRequestURI().getPath();
            if("/".equals(uri)) uri="/index.html";
            File f = new File(ROOT, uri.substring(1));
            if(!f.exists()||!f.isFile()){ sendError(ex,404,"Not found: "+uri); return; }
            String ext = uri.contains(".")?uri.substring(uri.lastIndexOf(".")):"";
            byte[] data = Files.readAllBytes(f.toPath());
            ex.getResponseHeaders().set("Content-Type", MIME.getOrDefault(ext.toLowerCase(),"text/plain"));
            ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
            ex.sendResponseHeaders(200,data.length);
            ex.getResponseBody().write(data); ex.getResponseBody().close();
        }
    }

    // ===================== MAIN =====================

    public static void main(String[] args) throws Exception {
        initData();
        initMySQL();        // Connect to MySQL auth_system database
        startSensorThread();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/login",      new LoginHandler());
        server.createContext("/api/signup",     new SignupHandler());
        server.createContext("/api/logout",     new LogoutHandler());
        server.createContext("/api/bins",       new BinsHandler());
        server.createContext("/api/alerts",     new AlertsHandler());
        server.createContext("/api/stats",      new StatsHandler());
        server.createContext("/api/detections", new DetectionsHandler());
        server.createContext("/api/zones",      new ZonesHandler());
        server.createContext("/api/reports",    new ReportsHandler());
        server.createContext("/",               new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("\n  ✅  SmartSeg JAVA Backend running!");
        System.out.println("  → http://localhost:" + PORT + "/index.html\n");
        System.out.println("  Java REST API:");
        System.out.println("  POST /api/login          — authenticate");
        System.out.println("  POST /api/signup         — register");
        System.out.println("  GET  /api/bins           — all bins (live data)");
        System.out.println("  POST /api/bins/{id}/pickup   — schedule pickup");
        System.out.println("  POST /api/bins/{id}/maintain — log maintenance");
        System.out.println("  GET  /api/alerts         — system alerts");
        System.out.println("  POST /api/alerts/{id}/dismiss — dismiss alert");
        System.out.println("  GET  /api/stats          — dashboard stats");
        System.out.println("  GET  /api/detections     — AI detection log");
        System.out.println("  POST /api/detections     — classify waste item");
        System.out.println("  GET  /api/zones          — zone summary");
        System.out.println("  GET  /api/reports        — full report\n");
        System.out.println("  Demo logins:");
        System.out.println("  admin/segregate2025  |  operator/greenbin");
        System.out.println("  supervisor/eco123    |  tech/smartbin\n");
    }
}
