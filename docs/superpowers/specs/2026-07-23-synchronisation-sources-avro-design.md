# Synchronisation des sources Java générées par Avro

## Contexte

Le plugin `avro-maven-plugin` 1.11.2 exécute le goal `schema` pendant la
phase Maven `generate-sources`. Les schémas placés dans
`src/main/resources/avro` sont les sources de vérité et les classes Java
produites sont écrites dans `src/main/java`.

Les classes `fr.abes.LigneKbartConnect` et `fr.abes.LigneKbartImprime` sont
donc suivies par Git, mais leur version actuellement committée ne correspond
plus exactement à la sortie du générateur. Le commit `957eb45` avait nettoyé
et réordonné manuellement leurs imports. Depuis, toute exécution de
`mvn generate-sources`, `mvn test` ou d'une phase Maven ultérieure rétablit
les imports du générateur et laisse le worktree modifié.

Le diff constaté est limité aux imports. Il ne modifie ni les schémas Avro,
ni les champs, ni les valeurs de `serialVersionUID`, ni le code de
sérialisation.

## Objectifs

- remettre les deux classes suivies par Git en conformité avec la sortie du
  générateur actuellement configuré ;
- garantir qu'une génération Maven répétée ne crée plus de diff Git ;
- documenter la source de vérité et la procédure de régénération ;
- conserver strictement les contrats Avro et Kafka existants.

## Hors périmètre

- mise à niveau des versions Avro ;
- alignement de la bibliothèque Avro 1.12.1 et du plugin 1.11.2 ;
- déplacement des sources générées vers `target/generated-sources` ;
- modification des schémas `.avsc` ;
- correction des tests Spring qui ne s'exécutent pas à cause de la
  configuration Log4j/Kafka préexistante.

Ces évolutions peuvent être étudiées séparément, car elles ont un périmètre
et un risque supérieurs à cette maintenance.

## Décision

La solution retenue est la synchronisation minimale :

1. conserver sans modification la configuration Maven et les versions Avro ;
2. conserver les classes générées sous `src/main/java` et suivies par Git ;
3. committer exactement la sortie produite par le plugin actuel pour
   `LigneKbartConnect` et `LigneKbartImprime` ;
4. ajouter une section de documentation au guide de développement indiquant
   que les fichiers générés ne doivent pas être édités manuellement ;
5. préciser que toute modification d'un schéma ou du générateur doit être
   suivie d'une régénération et du commit de l'intégralité du diff généré.

Cette option minimise le risque de régression : elle ne change ni
l'architecture du build, ni les dépendances, ni les artefacts fonctionnels.

## Validation

La maintenance est considérée valide si :

1. le diff des schémas `.avsc` par rapport à `develop` est vide ;
2. le diff des classes générées ne contient que les imports produits par
   `avro-maven-plugin` 1.11.2 ;
3. `mvn clean test` termine avec le code de sortie `0` ;
4. une nouvelle exécution de `mvn generate-sources` ne produit aucun diff ;
5. les tests SOA-501 restent tous exécutés avec zéro échec ;
6. la branche ne contient que les deux classes générées, la documentation de
   développement et la présente spécification.

## Risques et retour arrière

Le risque fonctionnel est faible, car le bytecode métier et les schémas ne
changent pas. Le principal risque est un futur nettoyage manuel des imports,
qui recréerait la dérive. La documentation doit donc rendre explicite que le
contenu généré doit rester identique à la sortie Maven, y compris lorsqu'un
import paraît inutile.

Le retour arrière consiste à annuler le commit de maintenance. Aucun
traitement de données ni migration n'est nécessaire.
