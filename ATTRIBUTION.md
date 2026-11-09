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

Périmètre : le backend (core, iam, api), le front-end PWA, les scripts d'installation/déploiement et la documentation ont été générés en grande partie par IA, à partir de nos spécifications (cahier des charges GreenPilot) et du squelette du cours. L'équipe a défini les besoins, installé et configuré l'environnement, testé de bout en bout, signalé les erreurs à corriger, relu le code et fait les commits. Les corrections demandées par l'enseignant (enums, requêtes des repositories, `pom.xml`) sont faites à la main par l'équipe.

### 📝 Journal des invites (Prompts) notables et composants générés par IA :
| Composant / Module | Outil utilisé (ChatGPT/Claude/Copilot...) | Description du prompt ou du rôle confié à l'IA |
| :--- | :--- | :--- |
| Structure Maven multi-module `middleware/` (core / iam / api) | Claude (opus 5.5)| Analyser le squelette du cours et l'adapter à GreenPilot : build multi-module Java 25 / Jakarta EE 11, patron BCE |
| `core` (NosqlEntity, UUIDv7, ETag, vérification JWT EdDSA, `@Secured`, CORS, gestion des erreurs) | Claude (opus 5.5)| Générer le module partagé utilisé par iam.war et greenpilot.war |
| `iam` (serveur OAuth 2.0 Authorization Code + PKCE, JWKS, rotation des refresh tokens, gestion des identités) | Claude(opus 5.5) | Générer un module IAM séparé avec jetons JWT signés Ed25519, rôles GROWER/ADMIN et scopes |
| `api` (entités, repositories Jakarta Data, managers, ressources REST, WebSocket, client MQTT v5) | Claude (opus 5.5)| Générer le middleware à partir du cahier des charges : télémétrie, prévisions, inspections, incidents, irrigation, modèles ML, audit |
| Contrat MQTT v5 (topics, payloads, ACL, TLS) | Claude (opus 5.5)| Définir les topics passerelle ↔ cloud, l'idempotence et les accusés de réception |
| Scripts `setup/` (installation Windows, certificats, ACL Mosquitto, build, lancement WildFly, serveur du front) | Claude(opus 5.5) | Générer les scripts PowerShell d'installation et de lancement en local |
| `samples/` (jeu de données, test de bout en bout, `PublishFile.java`) | Claude(opus 5.5) | Générer un test qui simule la passerelle et le client PWA |
| `front/` (PWA en HTML/CSS/JS : 4 pages producteur, 3 pages admin, service worker, manifest, icônes) | Claude (opus 5.5)| Remplacer le prototype React par une PWA sans framework connectée à l'API et à l'IAM |
| Prototype front-end React (supprimé depuis, conservé dans l'historique Git) | Claude (sonnet 5) | Première version de l'interface |
| `README.md` | Claude (opus 5.5)| Rédaction et mise à jour de la documentation du dépôt |
| Débogage | Claude (opus 5.5)| Erreurs de configuration (JAVA_HOME, stockage des `Instant` par JNoSQL, publication MQTT binaire sous Windows) |
---
## 📚 3. Emprunts, Squelettes de Cours & Plagiat
*Précisez si vous avez utilisé une base de code préexistante.*
*   **Code de départ enseignant / squelette du cours :**
    *   [x] Oui (indiquer quels modules : organisation core / iam / api, patron Boundary–Control–Entity, Jakarta NoSQL + Jakarta Data, principe des rôles stockés en bitmask ; utilisé comme référence d'architecture)
    *   [ ] Non
*   **Réutilisation d'anciens projets (Années précédentes / GitHub externes) :**
    *   [ ] Oui 
    *   [x] Non
    

Bibliothèques tierces : Jakarta EE 11 (WildFly 41), Eclipse JNoSQL + pilote MongoDB, Eclipse Paho MQTT v5, Mosquitto, Leaflet 1.9.4 (`front/vendor/leaflet`, licence BSD-2-Clause), fonds de carte OpenStreetMap.
---
## 🛠️ 4. Répartition Réelle de l'Écriture du Code
*Indiquez la provenance réelle (en %) des lignes de code actuellement sur votre branche principale.*
| Module / Composant | % Écrit à la main par l'équipe | % Généré par IA | % Emprunté / Réutilisé |
| :--- | :---: | :---: | :---: |
| **Passerelle IoT / Node-RED / Edge** | — % | — % | — % |
| **Backend / API REST / Middleware** | 10 % | 85 % | 5 % |
| **Sécurité (IAM / Keycloak / mTLS)** | 5 % | 90 % | 5 % |
| **Frontend / PWA** | 5 % | 90 % | 5 % |
| **Scripts d'Infra / Déploiement** | 5 % | 95 % | 0 % |

Notes : la partie passerelle/edge n'est pas encore sur la branche principale. « Emprunté » correspond à la structure reprise du squelette du cours et à la bibliothèque Leaflet. Ces pourcentages seront mis à jour à chaque sprint, notamment après les corrections faites à la main.
---
## ✍️ 5. Engagement sur l'Honneur
En validant ce fichier sur notre dépôt GitHub, nous certifions sur l'honneur que les déclarations ci-dessus sont exactes, sincères et reflètent fidèlement notre processus de développement. Nous sommes conscients que la falsification de ce document ou la non-maîtrise technique du code déclaré comme "écrit par l'équipe" lors des soutenances entraînera une **pénalité immédiate et sévère sur la note globale**.
*   [x] **Lu et approuvé par l'ensemble des membres de l'équipe.**
*   **Date de dernière mise à jour :** 09 / 11 / 2026
