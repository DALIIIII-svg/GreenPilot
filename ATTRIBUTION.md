# 📜 Déclaration d'Attribution et d'Intégrité Académique
### Module : Cloud of Things (COT) · Année 2026/2027 · SUP'COM
---
## 👥 1. Identification de l'Équipe
*   **Projet (Nom) :** GreenPilot
*   **Équipe (Numéro) :** 5
*   **Membres :**
    *   Membre 1 (Prénom, Nom) : Mohamed Ali El Filali
    *   Membre 2 (Prénom, Nom) : Mahdi Fakhfakh
---
## 🧠 2. Usage des Assistants de Génération (IA)
*Cochez l'option qui correspond à votre réalité et détaillez le périmètre.*
*   [ ] **Option A :** Aucun outil de génération de code par IA n'a été utilisé.
*   [ ] **Option B :** Utilisation modérée (Aide au débogage, génération de fonctions isolées, documentation).
*   [x] **Option C :** Utilisation intensive (Génération de structures de dossiers, squelettes d'API complets, scripts de déploiement).

Périmètre : le backend (core, iam, api), les PWA, la partie edge simulée (firmware ESP32 pour Wokwi, passerelle Node-RED, simulateur), la prévision d'humidité du sol (pipeline d'entraînement et service d'inférence sur la passerelle), le pipeline CI/CD (tests unitaires, GitHub Actions, kit de déploiement Linux), les scripts d'installation et la documentation ont été générés en grande partie par IA, à partir de nos spécifications (cahier des charges GreenPilot), du squelette du cours et des scripts CI/CD fournis par l'enseignant. L'équipe a défini les besoins et fait les choix (jeu de données, hébergement Azure, domaine), installé et configuré l'environnement local et le serveur Azure, testé de bout en bout (Wokwi → passerelle → cloud → PWA), signalé les erreurs à corriger, relu le code et fait les commits. 

### 📝 Journal des invites (Prompts) notables et composants générés par IA :
| Composant / Module | Outil utilisé (ChatGPT/Claude/Copilot...) | Description du prompt ou du rôle confié à l'IA |
| :--- | :--- | :--- |
| Structure Maven multi-module `middleware/` (core / iam / api) | Claude (opus 5.5)| Analyser le squelette du cours et l'adapter à GreenPilot : build multi-module Java 25 / Jakarta EE 11, patron BCE |
| `core` (NosqlEntity, UUIDv7, ETag, vérification JWT EdDSA, `@Secured`, CORS, gestion des erreurs) | Claude (opus 5.5)| Générer le module partagé utilisé par `iam-1.0.war` et `api-1.0.war` |
| `iam` (serveur OAuth 2.0 Authorization Code + PKCE, JWKS, rotation des refresh tokens, gestion des identités) | Claude(opus 5.5) | Générer un module IAM séparé avec jetons JWT signés Ed25519, rôles GROWER/ADMIN et scopes |
| `api` (entités, repositories Jakarta Data, managers, ressources REST, WebSocket, client MQTT v5) | Claude (opus 5.5)| Générer le middleware à partir du cahier des charges : télémétrie, prévisions, inspections, incidents, irrigation, modèles ML, audit |
| Entités : statuts et types convertis en enums (remarque de l'enseignant) | Claude (opus 5.5)| Appliquer la remarque de l'enseignant sur les classes qui devaient être des enums |
| Contrat MQTT v5 (topics, payloads, ACL, TLS) | Claude (opus 5.5)| Définir les topics passerelle ↔ cloud, l'idempotence et les accusés de réception |
| Scripts `setup/` (installation Windows, certificats, ACL Mosquitto, build, lancement WildFly) | Claude(opus 5.5) | Générer les scripts PowerShell d'installation et de lancement en local |
| Hébergement WildFly selon les scripts CI/CD de l'enseignant (`reset-hosts.cli`, `https.cli`, fichiers `.properties` dev/prod, `configure-wildfly-hosts.ps1`, HTTPS local) | Claude (opus 5.5)| Intégrer les scripts fournis sans les modifier : PWA, API et IAM servis par WildFly, chacun sur son hôte virtuel, en HTTPS |
| `samples/` (jeu de données, test de bout en bout, `PublishFile.java`) | Claude(opus 5.5) | Générer un test qui simule la passerelle et le client PWA |
| `frontend/` (PWA en HTML/CSS/JS : 4 pages producteur, 3 pages admin, service worker, manifest, icônes) | Claude (opus 5.5)| Remplacer le prototype React par une PWA sans framework connectée à l'API et à l'IAM |
| Séparation Front Office / Back Office (`frontend/frontoffice`, `frontend/backoffice`) et premiers scripts WildFly | Claude(sonnet 5.5) | - |
| Tableau de bord : prévision par zone et sur le graphique d'humidité | Claude (opus 5.5)| Afficher les prévisions de la passerelle dans la PWA producteur |
| `iot/` (firmware ESP32 et circuit Wokwi, passerelle Node-RED : buffer hors ligne, contrôle des commandes, irrigation locale, Thing Description W3C WoT ; simulateur Node.js) | Claude (opus 5.5)| Simuler la partie matérielle en attendant le matériel, selon le contrat ESP32 ↔ passerelle ↔ cloud |
| `model/forecast/` et `iot/edge-ml/` (prévision d'humidité à t + 30 min : préparation des données, comparaison persistance / régression ridge / arbres boostés, release versionnée ; service d'inférence sur la passerelle) | Claude (opus 5.5)| Entraîner et intégrer la prévision selon le §4.1 du cahier des charges, sur un jeu de données public en attendant nos propres cycles |
| `model/vision/` (classification des feuilles de poivron, YOLO11n-cls) | Claude(sonnet 5.5) | - |
| Tests unitaires JUnit, workflows GitHub Actions (`ci.yml`, `deploy.yml`), kit de déploiement Linux (`deploy/`) | Claude (opus 5.5)| Mettre en place le pipeline CI/CD et le déploiement sur la VM Azure |
| Prototype front-end React (supprimé depuis, conservé dans l'historique Git) | Claude (sonnet 5) | Première version de l'interface |
| `README.md` | Claude (opus 5.5)| Rédaction et mise à jour de la documentation du dépôt |
| Débogage | Claude (opus 5.5)| Erreurs de configuration (JAVA_HOME, stockage des `Instant` par JNoSQL, publication MQTT binaire sous Windows, plantage SNTP de l'ESP32 sous Wokwi, horodatage par la passerelle, `keytool` sous PowerShell, CORS WildFly, création de la VM Azure) |
---
## 📚 3. Emprunts, Squelettes de Cours & Plagiat
*Précisez si vous avez utilisé une base de code préexistante.*
*   **Code de départ enseignant / squelette du cours :**
    *   [x] Oui (indiquer quels modules : organisation core / iam / api, patron Boundary–Control–Entity, Jakarta NoSQL + Jakarta Data, principe des rôles stockés en bitmask, utilisés comme référence d'architecture ; scripts CI/CD de l'enseignant `default-host.cli`, `add-host.cli`, `add-api.cli` et modèles de `.properties`, repris sans modification dans `setup/wildfly/`)
    *   [ ] Non
*   **Réutilisation d'anciens projets (Années précédentes / GitHub externes) :**
    *   [ ] Oui 
    *   [x] Non
    

Bibliothèques tierces : Jakarta EE 11 (WildFly 41), Eclipse JNoSQL + pilote MongoDB, Eclipse Paho MQTT v5, Mosquitto, Leaflet 1.9.4 (`frontend/frontoffice/vendor/leaflet`, licence BSD-2-Clause), fonds de carte OpenStreetMap, JUnit 5, Node-RED, `ws`, ArduinoJson, DHTesp, WebSockets (links2004), scikit-learn / pandas / NumPy, Ultralytics YOLO11 (poids pré-entraînés, licence AGPL-3.0), Let's Encrypt (certbot).

Jeux de données : *Soil Moisture, Irrigation Actuator and Weather Dataset* (Arnesano, Italie, 2025 ; Mendeley Data, DOI 10.17632/c837v6p8ph.2, licence CC BY 4.0 ; météo Open-Meteo / ERA5, CC BY 4.0) pour la prévision ; PlantVillage (Kaggle) pour la vision.
---
## 🛠️ 4. Répartition Réelle de l'Écriture du Code
*Indiquez la provenance réelle (en %) des lignes de code actuellement sur votre branche principale.*
| Module / Composant | % Écrit à la main par l'équipe | % Généré par IA | % Emprunté / Réutilisé |
| :--- | :---: | :---: | :---: |
| **Passerelle IoT / Node-RED / Edge** | 5 % | 95 % | 0 % |
| **Modèle de prévision (entraînement + service edge)** | 5 % | 95 % | 0 % |
| **Modèle de vision** | 5 % | 95 %  | 0 % |
| **Backend / API REST / Middleware** | 10 % | 85 % | 5 % |
| **Sécurité (IAM / Keycloak / mTLS)** | 5 % | 90 % | 5 % |
| **Frontend / PWA** | 5 % | 90 % | 5 % |
| **Scripts d'Infra / Déploiement** | 5 % | 80 % | 15 % |

Notes : « Emprunté » correspond à la structure reprise du squelette du cours, à la bibliothèque Leaflet et, pour l'infrastructure, aux scripts CI/CD de l'enseignant repris tels quels. La partie edge est simulée (Wokwi, Node-RED sur PC) en attendant le matériel. Ces pourcentages seront mis à jour à chaque sprint, notamment après les corrections faites à la main.
---
## ✍️ 5. Engagement sur l'Honneur
En validant ce fichier sur notre dépôt GitHub, nous certifions sur l'honneur que les déclarations ci-dessus sont exactes, sincères et reflètent fidèlement notre processus de développement. Nous sommes conscients que la falsification de ce document ou la non-maîtrise technique du code déclaré comme "écrit par l'équipe" lors des soutenances entraînera une **pénalité immédiate et sévère sur la note globale**.
*   [x] **Lu et approuvé par l'ensemble des membres de l'équipe.**
*   **Date de dernière mise à jour :** 23 / 11 / 2026
