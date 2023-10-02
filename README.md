# best-ppn-api
API permettant à la fois de lire un topic kafka de lignes kbart, de calculer le best ppn et d'envoyer les lignes vers de nouveaux topics pour traitement ultérieur (insertion dans la base de Bacon, mise à jour de notices dans le Sudoc). Elle permet, par ailleurs, d'exposer un ws permettant de calculer le best ppn pour une ligne kafka donnée.

## Développement

### Génération de l'image docker
Vous pouvez avoir besoin de générer en local l'image docker de ``best-ppn-api`` par exemple si vous cherchez à modifier des liens entre les conteneurs docker de l'application.

Pour générer l'image docker de ``best-ppn-api`` en local voici la commande à lancer :
```bash
cd best-ppn-api/
docker build -t abesesr/convergence:develop-best-ppn-api .
```

Cette commande aura pour effet de générer une image docker sur votre poste en local avec le tag ``develop-best-ppn-api``. Vous pouvez alors déployer l'application en local avec docker en vous utilisant sur le [dépot ``convergence-bacon-docker``](https://github.com/abes-esr/convergence-bacon-docker) et en prenant soins de régler la variable ``BESTPPNAPI_VERSION`` sur la valeur ``develop-best-ppn-api`` (c'est sa [valeur par défaut](https://github.com/abes-esr/convergence-bacon-docker/blob/bdcd4302131eb86688ae729b0fc016d128f1ab9c/.env-dist#L9)) dans le fichier ``.env`` de votre déploiement [``convergence-bacon-docker``](https://github.com/abes-esr/convergence-bacon-docker).

Vous pouvez utiliser la même procédure pour générer en local les autres images docker applications composant l'architecture, la seule chose qui changera sera le nom du tag docker.


Cette commande suppose que vous disposez d'un environnement Docker en local : cf la [FAQ dans la poldev](https://github.com/abes-esr/abes-politique-developpement/blob/main/10-FAQ.md#configuration-dun-environnement-docker-sous-windows-10).

## Architecture globale de l'application
Best-ppn-api est un composant d'une architecture plus complète regroupant :
- kbart2kafka : programme permettant la lecture d'un fichier tsv contenant des lignes kbart et les envoyant sur un topic Kafka
- kafka2sudoc : programme permettant la lecture de topics kafka spécifiques en vue d'effectuer des modifications sur le Sudoc.
- best-ppn-api : programme permettant de lire un topic, de calculer le best ppn associé à une ligne kbart donnée et d'envoyer la ligne sur un topic en fonction du résultat

Le fonctionnement de ces API suppose la disponibilité d'un broker Kafka.

L'architecture générale de l'application peut être représentée par le schéma suivant : <br />
![archi_globale](https://github.com/abes-esr/best-ppn-api/assets/57490853/734ec2ec-6f11-4b9c-982a-280dbc81b8b8)

## schema registry et gestion des versions de schéma
### schema registry
Pour pouvoir utiliser le schema registry, il est nécessaire de l'installer avec Kafka. Voir le projet (insérer lien projet kafka Docker)

### Création du schéma
Les éléments composant les topics dans Kafka sont des objets de type ligne Kbart. Ils sont envoyés à kafka, en utilisant un serializer de type KafkaAvroSerializer. Pour permettre aux programmes Java et à Kafka de pouvoir dialoguer et de mapper correctement les données, il est nécessaire de créer un schéma représentant l'objet à envoyer. Pour cela : 
- dans best-ppn-api : dans le répertoire src/resources/avro se trouve un fichier ligne_kbart_convergence.avsc décrivant un record de ligne kbart en avro
- Il est nécessaire de générer la classe java correspondant à ce schéma via le goal maven generate-sources (mvn generate-sources).
- Le schéma doit ensuite être copié dans le schéma registry de Kafka pour que Kafka soit en mesure d'interpréter l'objet reçu dans le schéma
- Attention ! le fichier avro contient un numéro de version du schéma, il est indispensable que la classe java, et le schéma enregistré dans le schema registry aient le même numéro de version.
- le même schéma doit être utilisé dans toutes les sources de données qui liront le / les topics kafka contenant des objets de type ligneKbart.

Pour plus d'informations sur le format Avro : cliquer [ici](https://avro.apache.org/)

### kafka connect
Il est possible de lire le contenu d'un topic en utilisant kafka Connect. Voici la marche à suivre : 
- installer kafka connect sur le broker kafka
- facultatif : installer une interface utilisateur pour créer des sources de données kafka connect
- créer une source de données kafka-connect sur un topic en particulier (via l'IHM ou un appel web service en post)

Voici un exemple de configuration d'un sink kafka connect connecté à un topic et utilisant le schema registry pour effectuer le mapping topic -> table dans la base de données.
```json
{
  "connector.class": "io.confluent.connect.jdbc.JdbcSinkConnector",
  "connection.password": "",
  "tasks.max": "1",
  "max.retries": "3",
  "value.converter.enhanced.avro.schema.support": "true",
  "value.converter.schema.version": "n° version du schéma à utiliser pour ce topic",
  "value.converter": "io.confluent.connect.avro.AvroConverter",
  "dialect.name": "dialect dépendant de la bdd cible",
  "table.name.format": "schema.table destination",
  "topics": "topic à lire",
  "value.converter.schema.registry.url": "inserer_url_schema_registry",
  "connection.user": "user",
  "auto.create": "false",
  "connection.url": "inserer_url_jdbc_base_de_donnees",
  "pk.fields": "champ_cle_primaire",
  "quote.sql.identifiers": "never"
}
```



