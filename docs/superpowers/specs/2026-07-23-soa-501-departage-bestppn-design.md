# SOA-501 - Conception du départage des PPN ex aequo

## Statut

Approche fonctionnelle validée le 23 juillet 2026.
Spécification écrite en attente de relecture et de validation.

Sources fonctionnelles :

- ticket Jira `SOA-501` - « Affiner bestppn » ;
- document `Webservices_Convergence.pdf` ;
- ticket lié `CDE-477` pour le cas des notices multivolumes.

## Objectif

Réduire les cas où `best-ppn-api` ne produit aucun `bestppn` parce que plusieurs
PPN électroniques ont obtenu le même score maximal.

Le départage doit traiter les quatre cas décrits par SOA-501 :

1. ressource en ligne contre CD-ROM ou DVD-ROM ;
2. diffuseurs différents ;
3. volumes différents ;
4. années de publication différentes.

La sélection automatique reste conservatrice : une notice présentant une
contradiction forte avec la ligne KBART ne doit pas être choisie.

## Hors périmètre

- modification des webservices de recherche de `sudoc-api` ;
- modification des pondérations actuelles ;
- correction de l'enchaînement des appels `dat2ppn` ;
- contrôle du provider pour les publications en série ;
- rapprochement approximatif de titres au-delà du titre de partie `200$i` ;
- apprentissage automatique ou configuration dynamique des pondérations.

L'écart entre la documentation et le code pour les deux dates `dat2ppn` fait
l'objet d'un ticket séparé.

## État actuel

`BestPpnService` cumule les résultats de `onlineId2ppn`, `printId2ppn`,
`doi2ppn` et, en dernier recours, `dat2ppn`.

Les pondérations restent :

- `onlineId2ppn` : 10 ;
- `printId2ppn` avec rebond : 8 ;
- `printId2ppn` sans rebond : 6 ;
- `doi2ppn` : 15 ;
- `dat2ppn` : 20.

Lorsqu'un service renvoie plusieurs PPN, ses points sont divisés entre les PPN
trouvés. Les points sont ensuite additionnés par PPN.

Après extraction des PPN ayant le score maximal :

- aucun PPN électronique : traitement actuel des PPN imprimés ;
- un PPN électronique : sélection actuelle ;
- plusieurs PPN électroniques : erreur d'égalité ou valeur vide en mode forcé.

SOA-501 s'insère uniquement dans le troisième cas.

## Décision d'architecture

L'arbitrage est implémenté uniquement dans `best-ppn-api`.

Cette application possède déjà :

- les données KBART ;
- le provider du package ;
- les scores calculés ;
- un accès en lecture à la base XML ;
- `NoticeService` pour charger une notice par PPN ;
- la table BACON `PROVIDER`, qui fournit le code et le `DISPLAY_NAME`.

Le contrat JSON de `sudoc-api` reste inchangé. Aucun déploiement coordonné entre
les deux applications n'est nécessaire.

## Flux de traitement

```text
Recherche des candidats
        |
Calcul des scores existants
        |
Extraction des candidats au score maximal
        |
Un seul candidat --------------------------> bestPPN
        |
Plusieurs candidats
        |
Chargement des notices XML
        |
Calcul des rangs SUPPORT, DIFFUSEUR, VOLUME, ANNÉE
        |
Comparaison lexicographique
        |
Validation des contradictions fortes
        |
Candidat unique ---------------------------> bestPPN
        |
Toujours indécidable ----------------------> erreur actuelle inchangée
```

Les notices sont chargées uniquement pour les PPN ex aequo au score maximal.
Une notice n'est chargée qu'une fois pendant le traitement de la ligne KBART.

## Clé de décision

Pour chaque PPN `p`, la clé suivante est construite :

```text
clé(p) = (
    scoreActuel,
    rangSupport,
    rangDiffuseur,
    rangVolume,
    rangAnnée
)
```

Les clés sont comparées lexicographiquement, de gauche à droite et par ordre
décroissant.

Le `scoreActuel` reste le premier élément. Un PPN dont le score initial est
inférieur ne peut donc jamais revenir dans la sélection.

L'implémentation peut aussi appliquer la comparaison sous forme de cascade :

```text
T0 = PPN ayant le score maximal
T1 = PPN de T0 ayant le meilleur rang support
T2 = PPN de T1 ayant le meilleur rang diffuseur
T3 = PPN de T2 ayant le meilleur rang volume
T4 = PPN de T3 ayant le meilleur rang année
```

Lorsqu'une étape attribue le même rang à tous les candidats, l'ensemble reste
inchangé.

## Métadonnées extraites d'une notice

Une représentation interne immuable contient :

- le PPN ;
- le mode d'accès détaillé : `ONLINE`, `PHYSICAL` ou `UNKNOWN` ;
- les valeurs normalisées des `214 #2$c` ;
- les valeurs normalisées des `200$h` ;
- les titres de partie normalisés des `200$i` ;
- les années extraites de `100$a` et `214$d` ;
- un indicateur signalant une notice introuvable, supprimée ou illisible.

L'extraction reste indépendante de la ligne KBART. La comparaison avec KBART
est réalisée ensuite par le service d'arbitrage.

## Normalisation commune

Les comparaisons textuelles appliquent :

1. passage en minuscules ;
2. décomposition Unicode et suppression des diacritiques ;
3. remplacement de la ponctuation par des espaces ;
4. réduction des espaces multiples ;
5. suppression des espaces en début et fin.

La normalisation ne doit pas supprimer les chiffres.

## Critère SUPPORT

### Sources

- `530$b` ;
- `531$b` ;
- zone `215` et ses sous-zones descriptives ;
- `008` uniquement comme indication générale.

Le premier caractère `O` de `008` ne suffit pas à identifier un accès en
ligne, car il couvre également des supports comme le CD-ROM.

### Rangs

| Situation | Rang |
|---|---:|
| Mention explicite d'un accès en ligne | 2 |
| Mode d'accès absent, générique, hybride ou ambigu | 1 |
| Mention explicite d'un CD-ROM, DVD-ROM ou autre support matériel | 0 |

Exemples de marqueurs en ligne :

- `online` ;
- `en ligne` ;
- `ressource en ligne` ;
- `accès internet`.

Exemples de marqueurs physiques :

- `cd-rom` ou `cédérom` ;
- `dvd-rom` ;
- `disque optique`.

Le libellé générique `électronique` ne suffit pas à produire le rang 2.

Si une notice contient à la fois un marqueur en ligne et un marqueur physique,
son rang est 1.

### Contradiction forte

Un candidat de rang support 0 ne peut jamais être sélectionné automatiquement
pour une ligne KBART.

## Critère DIFFUSEUR

Ce critère s'applique uniquement aux monographies.

### Sources

Pour la ligne KBART :

- code `PROVIDER` ;
- `DISPLAY_NAME` associé dans la table BACON `PROVIDER`.

Pour la notice :

- zones `214` dont le second indicateur vaut `2` ;
- sous-zones `214 #2$c`.

Le contrôle général déjà réalisé pendant la recherche à partir de `035`,
`210$c`, `214$c` et des URL `856/859$u` reste inchangé.

Le booléen `providerPresent` ne sert pas directement au nouvel arbitrage. Il
agrège plusieurs contrôles et peut valoir vrai lorsqu'un provider n'est pas
référencé dans BACON.

### Rangs

| Situation | Rang |
|---|---:|
| Une `214 #2$c` correspond au code provider ou au `DISPLAY_NAME` | 2 |
| Provider inconnu ou aucune `214 #2$c` exploitable | 1 |
| Une `214 #2$c` existe mais désigne explicitement un autre diffuseur | 0 |

Une seule correspondance suffit lorsque plusieurs `214 #2$c` existent.

La correspondance utilise les valeurs normalisées. Pour les codes courts, le
code provider doit correspondre à un mot complet ; le `DISPLAY_NAME` peut être
recherché comme une expression normalisée.

### Contradiction forte

Le rang diffuseur 0 n'est pas une interdiction absolue. Une notice peut avoir
été légitimement repêchée par son URL d'accès.

## Critère VOLUME

Ce critère s'applique uniquement aux monographies.

### Sources

Pour la ligne KBART :

- `monograph_volume` ;
- à défaut, une mention de volume explicitement extraite de
  `publication_title`.

Pour la notice :

- `200$h` pour la désignation ou le numéro de partie ;
- `200$i` pour le titre de partie.

### Normalisation

Les préfixes usuels sont retirés :

- `tome` ;
- `volume` et `vol` ;
- `partie` et `part` ;
- `band`.

Les chiffres romains simples sont convertis en chiffres arabes. Ainsi `2`,
`02`, `Tome 2`, `Vol. 2` et `Part II` produisent la même clé `2`.

### Rangs

| Situation | Rang |
|---|---:|
| `monograph_volume` correspond exactement à `200$h` | 3 |
| `200$i` correspond au titre de partie contenu dans le titre KBART | 2 |
| La ligne KBART ne permet pas d'identifier un volume | 1 |
| Volume explicitement différent ou notice d'ensemble sans `200$h/$i` alors que KBART décrit un volume | 0 |

Si aucun volume fiable n'est disponible dans KBART, la seule présence de
`200$h` ou `200$i` ne suffit pas à choisir un candidat.

### Contradiction forte

Lorsqu'un volume est explicite dans KBART, un candidat de rang volume 0 ne peut
pas être sélectionné automatiquement.

## Critère ANNÉE

Ce critère s'applique uniquement aux monographies.

### Sources

Pour la ligne KBART, par priorité :

1. `date_monograph_published_online` ;
2. `date_monograph_published_print`.

Pour la notice :

- date 1 codée dans `100$a` ;
- années présentes dans les `214$d`.

Les formes `2021`, `DL 2021`, `cop. 2021` et `[2021]` produisent l'année
normalisée `2021`.

### Rangs

| Situation | Rang |
|---|---:|
| Une année Sudoc correspond à la date KBART en ligne | 3 |
| Aucune correspondance en ligne, mais correspondance avec la date imprimée | 2 |
| Aucune année Sudoc fiable ou données internes contradictoires | 1 |
| Les années Sudoc sont explicites et ne correspondent à aucune date KBART | 0 |

Une notice ne reçoit le rang 0 que lorsque ses différentes sources de date ne
se contredisent pas entre elles.

### Contradiction forte

Lorsque l'année KBART est explicite et que les années Sudoc sont cohérentes, un
candidat de rang année 0 ne peut pas être sélectionné automatiquement.

## Publications en série

Pour une publication en série :

- le critère support est évalué ;
- diffuseur, volume et année reçoivent le rang neutre 1.

Les contrôles supplémentaires sur les séries nécessitent une spécification
fonctionnelle distincte.

## Validation finale

Le candidat ayant la meilleure clé n'est sélectionné que si :

```text
rangSupport != 0
ET rangVolume != 0 lorsqu'un volume KBART est explicite
ET rangAnnée != 0 lorsque les dates KBART et Sudoc sont explicites et cohérentes
```

S'il existe plusieurs meilleures clés identiques, ou si le meilleur candidat
échoue à cette validation, l'erreur d'égalité actuelle est conservée.

Le mode forcé conserve également son comportement actuel : le `bestppn` reste
vide lorsque l'arbitrage n'aboutit pas.

## Composants

### `BestPpnService`

- conserve la recherche et le calcul du score ;
- transmet au service d'arbitrage la ligne KBART, le provider et les PPN ex
  aequo ;
- applique le résultat avant de produire l'erreur actuelle.

### `BestPpnTieBreakerService`

- orchestre l'arbitrage ;
- charge les notices par `NoticeService` ;
- récupère le code provider et le `DISPLAY_NAME` ;
- calcule les quatre rangs ;
- compare les clés ;
- applique les contradictions fortes ;
- produit une décision détaillée pour les logs.

### `CandidateMetadataExtractor`

- transforme une `NoticeXml` en métadonnées normalisées ;
- ne dépend ni de Kafka, ni de la base BACON, ni du calcul des scores ;
- se teste avec des notices XML construites en mémoire.

### Modèles internes

- `CandidateMetadata` : métadonnées extraites de la notice ;
- `CandidateDecisionKey` : rangs calculés et comparaison lexicographique ;
- `TieBreakDecision` : PPN sélectionné ou absence de décision, avec motif.

## Gestion des erreurs

- notice absente, supprimée ou illisible : métadonnées inconnues, sans arrêt du
  traitement ;
- provider absent de BACON : rang diffuseur neutre 1 ;
- zone mal formée : critère concerné neutre 1 ;
- exception d'accès à la base XML : journalisation technique, puis conservation
  de l'égalité ;
- aucune règle discriminante : erreur fonctionnelle actuelle.

L'arbitrage ne doit jamais transformer un problème de lecture en sélection
automatique.

## Journalisation

Pour chaque arbitrage :

- liste des PPN ex aequo et score initial ;
- valeurs KBART normalisées utilisées ;
- clé calculée pour chaque candidat ;
- zones ayant fourni les preuves ;
- candidats éliminés à chaque critère ;
- PPN retenu et critère décisif ;
- motif de l'absence de décision.

Exemple :

```text
Arbitrage SOA-501 : score=10, candidats=[123456789, 987654321]
PPN 123456789 : support=2, diffuseur=2, volume=3, année=3
PPN 987654321 : support=0, diffuseur=2, volume=3, année=3
PPN retenu : 123456789, critère décisif : SUPPORT
```

## Performance

- aucune notice XML n'est chargée en l'absence d'égalité ;
- seuls les candidats ayant le score maximal sont chargés ;
- chaque PPN est chargé une fois par ligne KBART ;
- aucun nouveau webservice n'est appelé ;
- aucune nouvelle dépendance n'est ajoutée.

Une optimisation par chargement groupé n'est envisagée que si les mesures de
production montrent que les lectures unitaires sont insuffisantes.

## Stratégie de tests

### Tests unitaires de l'extracteur

- support en ligne ;
- CD-ROM et DVD-ROM ;
- support absent ou hybride ;
- plusieurs `214`, dont une `214 #2$c` ;
- `200$h` numérique et en chiffres romains ;
- `200$i` correspondant au titre de partie ;
- extraction de `100$a` ;
- extraction de plusieurs `214$d` ;
- zones absentes et mal formées.

### Tests unitaires de l'arbitrage

- chaque critère départage deux candidats ;
- égalité inchangée lorsque le critère est neutre ;
- ordre support, diffuseur, volume, année ;
- score initial toujours prioritaire ;
- veto sur support physique ;
- veto sur volume incompatible ;
- veto sur année explicitement incompatible ;
- provider inconnu ;
- publication en série ;
- notice introuvable ;
- mode forcé avec arbitrage impossible.

### Tests d'intégration du service

- un seul candidat conserve le comportement actuel ;
- deux candidats départagés produisent un `BestPpn` électronique ;
- deux candidats non départagés produisent l'erreur actuelle ;
- aucun changement pour les parcours sans égalité ;
- aucun changement pour la sélection d'un PPN imprimé.

### Corpus de non-régression

Les exemples commentés de SOA-501 et les cas historiques issus des fichiers
`.bad` sont transformés en tests paramétrés après validation fonctionnelle du
PPN attendu.

## Critères d'acceptation

1. Les pondérations et recherches existantes ne changent pas.
2. L'arbitrage n'est exécuté que pour plusieurs PPN électroniques au score
   maximal.
3. Les quatre cas SOA-501 disposent de tests automatisés.
4. Un support physique n'est jamais sélectionné pour une ligne KBART.
5. Une contradiction forte empêche la sélection automatique.
6. Une égalité non résolue conserve exactement le comportement actuel.
7. Les logs expliquent le résultat sans nécessiter de recalcul manuel.
8. Le contrat de `sudoc-api` reste inchangé.
9. Aucun changement n'est apporté au comportement de `dat2ppn`.

## Stratégie Git

Dépôt concerné : `best-ppn-api`.

Branche :

```text
SOA-501-affiner-departage-bestppn
```

La branche part de `origin/develop` et utilise un worktree dédié. Les commits
sont rédigés en français et attribués à `Jerome Villiseck`.
