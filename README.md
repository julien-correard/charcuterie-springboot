

# 🥩 Application de gestion de commandes

Application web métier développée et maintenue en autonomie complète : de la définition du besoin avec le client jusqu'au support en production.

L'objectif : remplacer un processus manuel (téléphone, papier, tableur) par une application web sécurisée permettant à un exploitant agricole-charcutier et à ses clients de gérer leurs commandes hebdomadaires.

---

## 🎯 Contexte et motivation

Ce projet est né de la demande d'aide d'une amie, exploitante agricole-charcutière, pour digitaliser un processus de commande jusque-là géré au téléphone et sur tableur.

Au-delà du développement initial, l'application est **utilisée chaque semaine en conditions réelles**, ce qui implique un suivi continu : recueil et reformulation des besoins, priorisation des correctifs, diagnostic d'incidents en production, et communication régulière avec l'utilisatrice sur l'état du service et les évolutions.

---

## ✨ Fonctionnalités

### Espace client
- Authentification sécurisée par login/mot de passe
- Saisie de sa feuille de commande hebdomadaire par catégorie de produit (bœuf, veau, porc)
- Remise à zéro des quantités

### Espace administrateur
- **Tableau de commandes** : vue consolidée de toutes les commandes clients, avec suivi du réalisé
  - Colonne **"À faire"** : quantité restant à préparer par produit, calculée automatiquement pour les clients en mode Commande
  - Sauvegarde automatique en arrière-plan des quantités réalisées (batch, avec indicateur visuel de synchronisation ✔ / ⏳ / ❌)
- Synchronisation manuelle des commandes vers la vue admin
- Remise à zéro globale
- **Export Excel** (.xlsx) du récapitulatif des commandes
- **Gestion des produits** : création, modification, suppression, activation/désactivation
  - Un produit commandé ne peut pas être désactivé (protection via API)
- **Gestion des clients** : création, modification, suppression, assignation des produits accessibles et du mode de saisie

---

## 🛠️ Stack technique

| Couche | Technologie |
|---|---|
| Langage | Java 21 |
| Framework backend | Spring Boot 3.5 |
| Persistance | Spring Data JPA / Hibernate |
| Base de données | PostgreSQL |
| Sécurité | Spring Security (authentification par formulaire, rôles ADMIN/CLIENT) |
| Templates | Thymeleaf + Thymeleaf Security Extras |
| CSS / UI | Bootstrap 5 (thème Cerulean / Bootswatch) |
| Build | Maven |

---

## 🏗️ Architecture

Le projet suit une architecture MVC classique avec une séparation claire des responsabilités :

```
src/main/java/
├── controller/          # Contrôleurs Spring MVC (admin + client)
├── model/               # Entités JPA (User, Product, OrderItem, AdminOrderItem, Order)
├── repository/          # Interfaces Spring Data JPA
├── service/             # Logique métier
├── dto/                 # Objets de transfert de données (formulaires)
└── security/            # Configuration Spring Security, UserDetailsService
```

**Points d'architecture notables :**
- Séparation entre `OrderItem` (commandes brutes des clients) et `AdminOrderItem` (vue admin avec suivi du réalisé) — deux tables distinctes avec synchronisation contrôlée
- Clé composite (`OrderItemId`) pour les lignes de commande (userId + productId)
- Relation `@ManyToMany` entre `User` et `Product` pour les produits accessibles par client
- Sauvegarde en batch côté service (`saveOrUpdateBatch`) pour éviter les problèmes de performance liés aux écritures ligne par ligne
- Protection CSRF active sur tous les formulaires POST
- API REST partielle (`PATCH /admin/produits/{id}/active`, `POST /admin/commandes/batch`) pour les interactions JavaScript

---

## 📸 Aperçu des écrans

### Vue admin — Récapitulatif des commandes
Tableau croisé clients × produits, avec distinction visuelle par catégorie (couleurs), séparation entre clients en mode Stock et mode Commande, saisie inline du réalisé avec sauvegarde automatique, et colonne de synthèse des quantités restant à préparer.

![Tableau admin](screenshots/admin.png)

### Vue client — Feuille de commande
Interface épurée, centrée sur la saisie rapide par catégorie de produit.

![Tableau client](screenshots/client.png)

---

## 📚 Ce que ce projet m'a appris

Ce projet a été l'occasion de travailler sur des problématiques concrètes que l'on rencontre en entreprise :

- **Communication client** : recueil et reformulation des besoins auprès d'utilisateurs non technique, explications claires sur les incidents et les correctifs apportés
- **Diagnostic en production** : identification et résolution de bugs qui ne se manifestaient qu'avec des données réelles (requêtes lentes, conflits d'écriture, incohérences de synchronisation), à partir des logs applicatifs
- **Suivi et fiabilité** : mise en place d'un monitoring des logs en production, anticipation des écarts entre comportement local et comportement en conditions réelles
- **Modélisation de données** : penser les entités, les relations et les contraintes d'intégrité avant de coder
- **Sécurité web** : gestion des rôles, protection CSRF, encodage des mots de passe avec BCrypt
- **Expérience utilisateur** : donner du feedback visuel sur l'état des sauvegardes, gérer les cas limites (produit commandé qu'on ne peut pas supprimer)
- **Itération** : partir d'un besoin réel, le raffiner au fil des retours et des incidents rencontrés

---

## Ce que j'aurais pu améliorer

Ce projet m'a permis de comprendre la mise en œuvre des requêtes AJAX et de la synchronisation de données en arrière-plan. Avec davantage de recul, certains choix d'architecture initiaux auraient pu être anticipés plus tôt (notamment sur la gestion des sessions Hibernate et le traitement par lot).

L'apprentissage des bases de données SQL étant encore en progression, la modélisation et certaines requêtes auraient pu être améliorées dès la conception.

---

## ☁️ Déploiement — Application en production

L'application est **déployée et utilisée en production** sur [Railway](https://railway.app) :

🔗 **[https://serveur-commandes-production.up.railway.app](https://serveur-commandes-production.up.railway.app)**

### Infrastructure Railway
- **Service applicatif** : le JAR Spring Boot est buildé et exécuté directement par Railway via le `pom.xml` (pas de Dockerfile nécessaire)
- **Base de données** : instance PostgreSQL hébergée sur Railway, reliée à l'application via les variables d'environnement injectées automatiquement
- **Suivi** : logs applicatifs consultés via l'onglet Observability de Railway pour le diagnostic d'incidents

### Pourquoi pas de démo publique ?

L'application est utilisée en production, les données de commande sont confidentielles — aucun compte de démonstration n'est donc disponible publiquement.

## 🚀 Lancer le projet en local

### Prérequis
- Java 21
- Maven
- PostgreSQL

### Configuration
Créer une base de données PostgreSQL et mettre à jour `src/main/resources/application.properties` :

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/charcuterie
spring.datasource.username=votre_user
spring.datasource.password=votre_mot_de_passe
```

### Démarrage
```bash
./mvnw spring-boot:run
```

L'application est accessible sur `http://localhost:8080`.

---

## 👤 À propos

Projet conçu, développé et maintenu en autonomie complète : recueil des besoins, développement, déploiement, et support continu auprès d'une utilisatrice en conditions réelles.

---

*Projet réalisé en autodidacte — open to feedback !*