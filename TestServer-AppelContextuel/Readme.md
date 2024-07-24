# TestServer-AppelContextuel

Ce code constitue un outil pour tester l'appel contextuel DRIMbox en provenance des RIS/LPS. 

Prérequis :

- [Quarkus](https://quarkus.io/get-started/) (si Java n'est pas installé, Quarkus se chargera de l'installer pour vous)
- [Maven](https://maven.apache.org/install.html)


## Compilation et lancement

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


## Code

L'outil comprend 4 fichiers:
- ./src/main/java/appelContextuel/ParameterList.java : Définit les 2 services "/drim/302" et "/drim/303" et les paramètres supportés
- ./src/main/java/appelContextuel/DisplayPage.java : Définit le service "/drim/show" avec la page html à afficher présentant les paramètres passés lors du POST. 
- ./src/main/resources/templates/template.html : Template html utilisé par le service "drim/show"
- ./src/main/resources/application.properties : fichier de configuration pour définir certaines variables, notamment la variable contenant le nom d'hôte du serveur.

L'outil ne fait pas de contrôle de format ou de cohérence aujourd'hui sur les valeurs passées lors de l'appel. 
En revanche, il vérifie que la valeur associée à un paramètre obligatoire n'est pas nulle.

## FAQ

- Que faire pour ajouter/modifier/supprimer un paramètre ?
  
  Ajouter/modifier/supprimer le paramètre dans la variable "paramsAll" au début du fichier "ParameterList.java".
  S'il s'agit d'un paramètre obligatoire, modifier également la variable "paramsMandatory".
  Il n'y a pas d'autres modifications à faire, la page d'affichage des résultats prendra automatiquement en compte les modifications.