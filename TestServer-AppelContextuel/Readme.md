# TestServer-AppelContextuel

Ce code constitue un outil pour tester l'appel contextuel DRIMbox en provenance des RIS/LPS. 

Prérequis :

- [Quarkus](https://quarkus.io/get-started/) (si Java n'est pas installé, Quarkus se chargera de l'installer pour vous)
- [Maven](https://maven.apache.org/install.html)


## Compilation

```bash
./mvnw compile quarkus:dev
```

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


## Configuration

L'url de déploiement de l'application peut être configurée dans 
 - le fichier application.properties sous la forme : `server.hostname = http://demodrim.labs.b-com.com`
 - dans le docker-compose.yml sous la forme : `server_hostname: http://demodrim.labs.b-com.com`


L'outil est alors disponible aux urls suivantes (via un appel contextuel POST conforme aux exigences DRIMbox) :
 - `http://demodrim.labs.b-com.com/drim/302` pour avoir une réponse HTTP 302
 - `http://demodrim.labs.b-com.com/drim/303` pour avoir une réponse HTTP 303

 Les différents paramètres passés lors de l'appel POST sont finalement accessibles via l'url de la forme `http://demodrim.labs.b-com.com/drim/show?uuid=...` indiqué dans la réponse du POST.

