# TestServer-AppelContextuel

Ce code constitue un outil pour tester l'appel contextuel DRIMbox en provenance des RIS/LPS. 

Prérequis :

- [Quarkus](https://quarkus.io/get-started/) (si Java n'est pas installé, Quarkus se chargera de l'installer pour vous)
- [Maven](https://maven.apache.org/install.html)


## Compilation

```bash
./mvnw compile quarkus:dev
```

## Lancement

L'outil est alors disponible aux urls suivantes (via un appel contextuel POST conforme aux exigences DRIMbox) :
 - `http://localhost:8080/drim/302` pour avoir une réponse HTTP 302
 - `http://localhost:8080/drim/303` pour avoir une réponse HTTP 303

Les différents paramètres passés lors de l'appel POST sont alors accessibles via l'url de la forme `http://localhost:8080/show?uuid=...` indiqué dans la réponse du POST.


## Docker

L'outil est également disponible via Docker.

Pour construire l'image
```bash
docker-compose build
```

Pour lancer l'outil:
```bash
docker-compose up
```

L'outil est alors disponible sur le port 80.
 - `http://localhost/drim/302` pour avoir une réponse HTTP 302
 - `http://localhost/drim/303` pour avoir une réponse HTTP 303

