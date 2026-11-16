
package com.softmax.shardaos.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FirebaseAuthService {

    @Value("${firebase.config.path}")
    private String firebaseConfigPath;

    @PostConstruct
    public void init() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                System.out.println("Firebase already initialized, skipping");
                return;
            }

            System.out.println("Initializing Firebase...");
            System.out.println("Firebase config path = " + firebaseConfigPath);

            InputStream serviceAccount;

            Path filePath = Path.of(firebaseConfigPath);

            if (filePath.isAbsolute() && Files.isRegularFile(filePath)) {
                // External JSON file on VM
                serviceAccount = Files.newInputStream(filePath);
                System.out.println("Loading Firebase config from filesystem");
            } else {
                // Local development: src/main/resources
                ClassPathResource resource =
                        new ClassPathResource(firebaseConfigPath);

                if (!resource.exists()) {
                    throw new IllegalStateException(
                            "Firebase config not found: " + firebaseConfigPath
                    );
                }

                serviceAccount = resource.getInputStream();
                System.out.println("Loading Firebase config from classpath");
            }

            try (InputStream input = serviceAccount) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(input))
                        .build();

                FirebaseApp app = FirebaseApp.initializeApp(options);

                System.out.println("Firebase Initialized Successfully");
                System.out.println(
                        "Firebase projectId = " + app.getOptions().getProjectId()
                );
            }

        } catch (Exception e) {
            System.err.println("Firebase Initialization Failed");
            e.printStackTrace();
            throw new RuntimeException("Firebase init failed", e);
        }
    }

    public FirebaseToken verifyToken(String token) {
        try {
            FirebaseToken decoded =
                    FirebaseAuth.getInstance().verifyIdToken(token);

            System.out.println("Firebase token verified");
            return decoded;

        } catch (Exception e) {
            System.err.println("Firebase token verification failed");
            e.printStackTrace();
            throw new RuntimeException("Invalid Firebase Token", e);
        }
    }
}






//package com.softmax.shardaos.service;
//
//import com.google.auth.oauth2.GoogleCredentials;
//import com.google.firebase.FirebaseApp;
//import com.google.firebase.FirebaseOptions;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseToken;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.stereotype.Service;
//
//import jakarta.annotation.PostConstruct;
//import java.io.InputStream;
//
//@Service
//public class FirebaseAuthService {
//
//    // Reads from application.properties: firebase.config.path
//    // e.g. firebase.config.path=softmaxcrm-firebase-adminsdk-fbsvc-2addcdee2e.json
//    @Value("${firebase.config.path}")
//    private String firebaseConfigPath;
//
//    @PostConstruct
//    public void init() {
//        try {
//            if (!FirebaseApp.getApps().isEmpty()) {
//                System.out.println("⚠️ Firebase already initialized, skipping");
//                return;
//            }
//
//            System.out.println("🔥 Initializing Firebase...");
//            System.out.println("🔥 Config file (classpath) = " + firebaseConfigPath);
//
//            // Try classpath first (works for both src/main/resources and JAR packaging)
//            InputStream serviceAccount =
//                    new ClassPathResource(firebaseConfigPath).getInputStream();
//
//            if (serviceAccount == null) {
//                throw new RuntimeException("❌ Firebase config JSON NOT FOUND on classpath: "
//                        + firebaseConfigPath);
//            }
//
//            FirebaseOptions options = FirebaseOptions.builder()
//                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
//                    .build();
//
//            FirebaseApp app = FirebaseApp.initializeApp(options);
//
//            System.out.println("✅ Firebase Initialized Successfully");
//            System.out.println("🔥 FirebaseApp projectId = " + app.getOptions().getProjectId());
//
//        } catch (Exception e) {
//            System.out.println("❌ Firebase Initialization Failed");
//            e.printStackTrace();
//            throw new RuntimeException("Firebase init failed", e);
//        }
//    }
//
//    public FirebaseToken verifyToken(String token) {
//        try {
//            System.out.println("🔥 VERIFYING TOKEN...");
//            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
//
//            System.out.println("✅ TOKEN VERIFIED");
//            System.out.println("UID: " + decoded.getUid());
//            System.out.println("EMAIL: " + decoded.getEmail());
//
//            return decoded;
//        } catch (Exception e) {
//            System.out.println("❌ TOKEN VERIFY FAILED");
//            e.printStackTrace();
//            throw new RuntimeException("Invalid Firebase Token");
//        }
//    }
//}
//













//package com.softmax.shardaos.service;
//
//import com.google.auth.oauth2.GoogleCredentials;
//import com.google.firebase.FirebaseApp;
//import com.google.firebase.FirebaseOptions;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseToken;
//import org.springframework.stereotype.Service;
//
//import jakarta.annotation.PostConstruct;
//import java.io.InputStream;
//
//@Service
//public class FirebaseAuthService {
//
//    @PostConstruct
//    public void init() {
//        try {
//
//            if (FirebaseApp.getApps().isEmpty()) {
//
//                System.out.println("🔥 Initializing Firebase...");
//
//                InputStream serviceAccount =
//                        getClass().getClassLoader().getResourceAsStream("gaurionbharat-eaae4-firebase-adminsdk-fbsvc-82d31d954c.json");
//
//                if (serviceAccount == null) {
//                    throw new RuntimeException("❌ serviceAccountKey.json NOT FOUND in resources");
//                }
//
//                FirebaseOptions options = FirebaseOptions.builder()
//                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
//                        .build();
//
//                FirebaseApp.initializeApp(options);
//
//                System.out.println("✅ Firebase Initialized Successfully");
//            }
//
//        } catch (Exception e) {
//            System.out.println("❌ Firebase Initialization Failed");
//            e.printStackTrace();
//        }
//    }
//
//    public FirebaseToken verifyToken(String token) {
//        try {
//            System.out.println("🔥 VERIFYING TOKEN...");
//            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token);
//
//            System.out.println("✅ TOKEN VERIFIED");
//            System.out.println("UID: " + decoded.getUid());
//            System.out.println("EMAIL: " + decoded.getEmail());
//
//            return decoded;
//
//        } catch (Exception e) {
//            System.out.println("❌ TOKEN VERIFY FAILED");
//            e.printStackTrace();
//            throw new RuntimeException("Invalid Firebase Token");
//        }
//    }
//}